package org.jeecg.modules.siteplatform;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ResourceService {
    private final ResourceRepository repository;
    private final ResourceAccess access;
    private final DnsTxtResolver dns;
    private final SecureRandom random = new SecureRandom();

    public ResourceService(ResourceRepository repository, ResourceAccess access, DnsTxtResolver dns) {
        this.repository = repository; this.access = access; this.dns = dns;
    }

    private Map<String, Object> requireSite(ResourceAccess.Actor actor, String site, boolean write, boolean admin) {
        Map<String, Object> row = repository.site(actor.tenantId(), site, write);
        if (write && row != null && !repository.activeMemberCurrent(actor.tenantId(), actor.userId())) {
            throw new ResourceFailure(403, "资源不可访问或无操作权限");
        }
        String role = row == null ? null : repository.memberRole(actor.tenantId(), site, actor.userId());
        if (role == null || !Set.of("ADMIN", "EDITOR", "VIEWER").contains(role) || (write && "VIEWER".equals(role)) || (admin && !"ADMIN".equals(role))) {
            throw new ResourceFailure(403, "资源不可访问或无操作权限");
        }
        return row;
    }

    private void changed(int rows) { if (rows != 1) throw new ResourceFailure(409, "资源不存在、已变更或不可操作，请刷新后重试"); }
    private String token() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private boolean flag(Object value) { return Boolean.TRUE.equals(value) || (value instanceof Number n && n.intValue() == 1); }
    private boolean verified(Map<String, Object> row) { return flag(row.get("verified")); }

    public Map<String, Object> sites(int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw ResourceRules.invalid("分页范围无效");
        ResourceAccess.Actor actor = access.current();
        return Map.of("records", repository.sites(actor, (page - 1) * size, size), "total", repository.countSites(actor));
    }

    @Transactional
    public String createSite(ResourceRequests.SiteInput input) {
        ResourceAccess.Actor actor = access.current();
        ResourceRules.environment(input.environment());
        String id = repository.insertSite(actor, input);
        repository.audit(actor, id, "SITE_CREATE", id, input.name().trim() + " [" + input.environment() + "]");
        return id;
    }

    @Transactional
    public void updateSite(String site, ResourceRequests.SiteInput input) {
        ResourceAccess.Actor actor = access.current();
        Map<String, Object> previous = requireSite(actor, site, true, false);
        if (!input.environment().equals(previous.get("environment"))) throw ResourceRules.invalid("站点环境创建后不可修改，请新建对应环境的站点");
        repository.updateSite(actor.tenantId(), site, input);
        repository.audit(actor, site, "SITE_UPDATE", site, previous.get("name") + " -> " + input.name().trim() + "; enabled=" + input.enabled());
    }

    public List<Map<String, Object>> targets(String site) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, false, false);
        return repository.targets(actor.tenantId(), site);
    }

    @Transactional
    public String addTarget(String site, ResourceRequests.TargetInput input) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, true, false);
        String url = ResourceRules.targetUrl(input.url());
        String id = repository.insertTarget(actor.tenantId(), site, input, url);
        repository.audit(actor, site, "TARGET_CREATE", id, input.name().trim());
        return id;
    }

    @Transactional
    public void updateTarget(String site, String id, ResourceRequests.TargetInput input) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, true, false);
        changed(repository.updateTarget(actor.tenantId(), site, id, input, ResourceRules.targetUrl(input.url())));
        repository.audit(actor, site, "TARGET_UPDATE", id, input.name().trim() + "; enabled=" + input.enabled());
    }

    @Transactional
    public void deleteTarget(String site, String id) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, true, false);
        if (repository.liveTargetReferenced(actor.tenantId(), site, id)) throw new ResourceFailure(409, "目标正在被当前发布版本引用，请先发布替代版本");
        changed(repository.deleteTarget(actor.tenantId(), site, id));
        repository.audit(actor, site, "TARGET_DELETE", id, "解除资源登记");
    }

    public List<Map<String, Object>> domains(String site) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, false, false);
        return repository.domains(actor.tenantId(), site);
    }

    @Transactional
    public String bindDomain(String site, ResourceRequests.DomainInput input) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, true, false);
        String hostname = ResourceRules.hostname(input.hostname());
        if (("_jeecg-verify." + hostname).length() > 253) throw ResourceRules.invalid("域名过长，无法生成有效的 DNS 验证记录名");
        try {
            String id = repository.insertDomain(actor.tenantId(), site, hostname, token());
            repository.audit(actor, site, "DOMAIN_BIND", id, hostname);
            return id;
        } catch (DuplicateKeyException e) { throw new ResourceFailure(409, "域名已被绑定，不能重复占用"); }
    }

    private Map<String, Object> requireDomain(ResourceAccess.Actor actor, String site, String id) {
        Map<String, Object> row = repository.domain(actor.tenantId(), site, id);
        if (row == null) throw new ResourceFailure(403, "资源不可访问或无操作权限");
        return row;
    }

    public Map<String, Object> challenge(String site, String id) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, false, false);
        String role = repository.memberRole(actor.tenantId(), site, actor.userId());
        if ("VIEWER".equals(role)) throw new ResourceFailure(403, "只读成员不能获取验证挑战");
        Map<String, Object> row = requireDomain(actor, site, id);
        if (verified(row)) throw new ResourceFailure(409, "域名已验证");
        return Map.of("name", "_jeecg-verify." + row.get("hostname"), "value", "jeecg-site=" + row.get("challenge"), "expiresAt", row.get("challengeExpires"));
    }

    @Transactional
    public void renewChallenge(String site, String id) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, true, false);
        changed(repository.renewChallenge(actor.tenantId(), site, id, token()));
        repository.audit(actor, site, "DOMAIN_CHALLENGE_RENEW", id, "旧挑战失效");
    }

    @Transactional
    public void verifyDomain(String site, String id) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, true, false);
        Map<String, Object> row = requireDomain(actor, site, id);
        if (verified(row)) throw new ResourceFailure(409, "域名已验证");
        // Use the database clock and a boolean projection, not driver-specific DATETIME objects.
        if (!flag(row.get("challengeValid"))) throw new ResourceFailure(409, "验证挑战已过期，请重新生成");
        String challenge = row.get("challenge").toString();
        if (!dns.records("_jeecg-verify." + row.get("hostname")).contains("jeecg-site=" + challenge)) throw new ResourceFailure(422, "TXT 记录不匹配，请等待解析生效后重试");
        changed(repository.verifyDomain(actor.tenantId(), site, id, challenge));
        repository.audit(actor, site, "DOMAIN_VERIFY", id, row.get("hostname").toString());
    }

    @Transactional
    public void releaseDomain(String site, String id) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, true, false);
        Map<String, Object> row = requireDomain(actor, site, id);
        if (repository.publishedDomainReferenced(actor.tenantId(), site, id)) throw new ResourceFailure(409, "域名已有发布历史，需运维完成旧节点撤除后迁移");
        changed(repository.releaseDomain(actor.tenantId(), site, id));
        repository.audit(actor, site, "DOMAIN_RELEASE", id, row.get("hostname").toString());
    }

    public List<Map<String, Object>> members(String site) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, false, true);
        return repository.members(actor.tenantId(), site);
    }

    @Transactional
    public void putMember(String site, ResourceRequests.MemberInput input) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, true, true);
        String role = ResourceRules.role(input.role());
        if (actor.userId().equals(input.userId()) && !"ADMIN".equals(role)) throw ResourceRules.invalid("不能降低自己的管理员权限");
        if (!repository.activeMember(actor.tenantId(), input.userId())) throw ResourceRules.invalid("用户不是当前租户的有效成员");
        repository.upsertMember(actor.tenantId(), site, input.userId(), role);
        repository.audit(actor, site, "MEMBER_SET", input.userId(), role);
    }

    @Transactional
    public void removeMember(String site, String user) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, true, true);
        if (actor.userId().equals(user)) throw ResourceRules.invalid("不能移除自己的管理员权限");
        changed(repository.removeMember(actor.tenantId(), site, user));
        repository.audit(actor, site, "MEMBER_REMOVE", user, "撤销站点权限");
    }

    public List<Map<String, Object>> audit(String site) {
        ResourceAccess.Actor actor = access.current(); requireSite(actor, site, false, false);
        return repository.auditEntries(actor.tenantId(), site);
    }
}
