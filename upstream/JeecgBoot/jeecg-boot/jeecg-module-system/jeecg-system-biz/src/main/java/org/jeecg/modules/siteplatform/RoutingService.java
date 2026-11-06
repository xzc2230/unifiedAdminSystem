package org.jeecg.modules.siteplatform;

import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;

@Service
public class RoutingService {
    private final RoutingRepository repository;
    private final ResourceRepository resources;
    private final ResourceAccess access;
    public RoutingService(RoutingRepository repository, ResourceRepository resources, ResourceAccess access) {
        this.repository = repository; this.resources = resources; this.access = access;
    }
    private record Context(ResourceAccess.Actor actor, Map<String,Object> site) {}
    private Context require(String site, boolean write, boolean admin) {
        ResourceAccess.Actor actor = access.current();
        // Same parent as resource edits/revocation, BEFORE current membership reads.
        var row = resources.site(actor.tenantId(), site, true);
        if (row == null || !repository.activeMemberCurrent(actor.tenantId(), actor.userId())) throw forbidden();
        String role = resources.memberRole(actor.tenantId(), site, actor.userId());
        if (role == null || !Set.of("ADMIN", "EDITOR", "VIEWER").contains(role) || write && "VIEWER".equals(role) || admin && !"ADMIN".equals(role)) throw forbidden();
        return new Context(actor, row);
    }
    private static ResourceFailure forbidden() { return new ResourceFailure(403, "资源不可访问或无操作权限"); }
    private static ResourceFailure conflict() { return new ResourceFailure(409, "草稿版本或状态已变更，请刷新后重试"); }
    private RoutingRepository.Draft draft(Context context, String site, long revision, String status) {
        if (revision < 0 || revision >= 9007199254740991L) throw ResourceRules.invalid("草稿版本无效");
        var draft = repository.draft(context.actor().tenantId(), site);
        if (draft == null || draft.revision() != revision || status != null && !status.equals(draft.status())) throw conflict();
        return draft;
    }
    private RoutingRules.Candidate candidate(Context context, String site, RoutingRequests.DraftInput input, String releaseId) {
        int tenant = context.actor().tenantId();
        return RoutingRules.candidate(tenant, site, context.site(), input, repository.domains(tenant, site), repository.targets(tenant, site), releaseId);
    }
    private RoutingRules.Candidate revalidate(Context context, String site, String input, String payload, String hash, String state) {
        try {
            if (payload == null || hash == null || !RoutingRules.hash(payload).equals(hash)) throw RoutingRules.unavailable();
            String id = JSON.parseObject(payload).getString("releaseId");
            if (!RoutingRules.identifier(id)) throw RoutingRules.unavailable();
            var current = candidate(context, site, RoutingRules.input(input), id);
            if (!current.payload().equals(payload) || !current.hash().equals(hash) || !current.resourceState().equals(state)) throw RoutingRules.unavailable();
            return current;
        } catch (ResourceFailure e) { throw RoutingRules.unavailable(); }
        catch (RuntimeException e) { throw RoutingRules.unavailable(); }
    }
    private void audit(Context context, String site, String action, String id) {
        resources.audit(context.actor(), site, action, id, "路由草稿与发布状态变更");
    }
    private RoutingRequests.DraftInput atRevision(RoutingRequests.DraftInput input, long revision) {
        return new RoutingRequests.DraftInput(revision, input.defaultTargetId(), input.domainIds(), input.rules());
    }
    @Transactional
    public Map<String,Object> view(String site) {
        var context = require(site, false, false);
        var draft = repository.draft(context.actor().tenantId(), site);
        Object view = draft == null ? null : RoutingRules.object("revision", draft.revision(), "status", draft.status(),
                "input", atRevision(RoutingRules.input(draft.inputJson()), draft.revision()), "submittedBy", draft.submittedBy(), "approvedBy", draft.approvedBy());
        return RoutingRules.object("draft", view, "releases", repository.releases(context.actor().tenantId(), site));
    }
    @Transactional
    public long save(String site, RoutingRequests.DraftInput raw) {
        var context = require(site, true, false);
        var input = RoutingRules.normalize(raw);
        var previous = repository.draft(context.actor().tenantId(), site);
        if ((previous == null ? 0 : previous.revision()) != input.revision()) throw conflict();
        // Drafts may reference unverified/disabled inventory. Always enforce ownership.
        int tenant = context.actor().tenantId();
        var domainIds = new HashSet<String>(); repository.domains(tenant, site).forEach(row -> domainIds.add(row.get("id").toString()));
        var targetIds = new HashSet<String>(); repository.targets(tenant, site).forEach(row -> targetIds.add(row.get("id").toString()));
        if (!domainIds.containsAll(input.domainIds()) || !targetIds.contains(input.defaultTargetId()) ||
                input.rules().stream().anyMatch(rule -> !targetIds.contains(rule.targetId()))) throw RoutingRules.unavailable();
        long next = input.revision() + 1;
        repository.save(tenant, site, new RoutingRepository.Draft(next, "DRAFT", RoutingRules.json(atRevision(input, next)), null, null, null, null, null, null));
        audit(context, site, "ROUTING_SAVE", site); return next;
    }
    @Transactional
    public long submit(String site, long revision) {
        var context = require(site, true, false);
        var previous = draft(context, site, revision, "DRAFT");
        if (previous.sourceReleaseId() != null) {
            var source = repository.release(context.actor().tenantId(), site, previous.sourceReleaseId());
            if (source == null) throw RoutingRules.unavailable();
            revalidate(context, site, source.get("inputJson").toString(), source.get("payload").toString(), source.get("hash").toString(), source.get("resourceState").toString());
        }
        // Allocate once at submission; approval/publish preserve exact bytes and identity.
        var candidate = candidate(context, site, RoutingRules.input(previous.inputJson()), ResourceRepository.id());
        long next = revision + 1;
        repository.save(context.actor().tenantId(), site, new RoutingRepository.Draft(next, "SUBMITTED", previous.inputJson(), candidate.payload(), candidate.hash(),
                candidate.resourceState(), context.actor().userId(), null, previous.sourceReleaseId()));
        audit(context, site, "ROUTING_SUBMIT", site); return next;
    }
    @Transactional
    public long approve(String site, long revision) {
        var context = require(site, true, true);
        var previous = draft(context, site, revision, "SUBMITTED");
        if ("prod".equals(context.site().get("environment")) && context.actor().userId().equals(previous.submittedBy()))
            throw new ResourceFailure(403, "生产环境提交人不能审批自己的变更");
        revalidate(context, site, previous.inputJson(), previous.payload(), previous.hash(), previous.resourceState());
        long next = revision + 1;
        repository.save(context.actor().tenantId(), site, new RoutingRepository.Draft(next, "APPROVED", previous.inputJson(), previous.payload(), previous.hash(),
                previous.resourceState(), previous.submittedBy(), context.actor().userId(), previous.sourceReleaseId()));
        audit(context, site, "ROUTING_APPROVE", site); return next;
    }
    @Transactional
    public long publish(String site, long revision) {
        var context = require(site, true, true);
        repository.lockPublication();
        var previous = draft(context, site, revision, "APPROVED");
        if (previous.submittedBy() == null || previous.approvedBy() == null ||
                "prod".equals(context.site().get("environment")) && previous.submittedBy().equals(previous.approvedBy())) throw conflict();
        revalidate(context, site, previous.inputJson(), previous.payload(), previous.hash(), previous.resourceState());
        RoutingRules.validateHostedSet(previous.payload(), repository.livePayloadsExcept(context.actor().tenantId(), site));
        String id = JSON.parseObject(previous.payload()).getString("releaseId");
        repository.publish(context.actor().tenantId(), site, id, previous, context.actor().userId());
        long next = revision + 1;
        repository.save(context.actor().tenantId(), site, new RoutingRepository.Draft(next, "DRAFT", previous.inputJson(), null, null, null, null, null, null));
        audit(context, site, "ROUTING_PUBLISH", id); return next;
    }
    @Transactional
    public long rollback(String site, String release, long revision) {
        var context = require(site, true, true);
        var previous = draft(context, site, revision, null);
        var source = repository.release(context.actor().tenantId(), site, release);
        if (source == null) throw forbidden();
        revalidate(context, site, source.get("inputJson").toString(), source.get("payload").toString(), source.get("hash").toString(), source.get("resourceState").toString());
        long next = previous.revision() + 1;
        repository.save(context.actor().tenantId(), site, new RoutingRepository.Draft(next, "DRAFT", source.get("inputJson").toString(), null, null, null, null, null, release));
        audit(context, site, "ROUTING_ROLLBACK_DRAFT", release); return next;
    }
    @Transactional
    public Map<String,Object> simulate(String site, RoutingRequests.SimulateInput request) {
        var context = require(site, false, false);
        if (request == null) throw ResourceRules.invalid("模拟输入无效");
        String hostname = ResourceRules.hostname(request.hostname()), path = RoutingRules.path(request.path());
        var candidate = candidate(context, site, RoutingRules.normalize(request.input()), "simulation");
        var data = JSON.parseObject(candidate.payload());
        if (!data.getJSONArray("domains").contains(hostname)) throw ResourceRules.invalid("模拟域名不在当前草稿中");
        String ruleId = null;
        var target = data.getJSONObject("defaultTarget");
        for (Object item : data.getJSONArray("rules")) {
            var rule = (com.alibaba.fastjson2.JSONObject) item;
            if ((rule.getString("hostname") == null || hostname.equals(rule.getString("hostname"))) && RoutingRules.matches(rule.getString("path"), rule.getString("matchType"), path)) {
                ruleId = rule.getString("id"); target = rule.getJSONObject("target"); break;
            }
        }
        URI url = URI.create(target.getString("url"));
        String base = url.getRawPath() == null ? "" : url.getRawPath().replaceFirst("/$", "");
        return RoutingRules.object("ruleId", ruleId, "targetId", target.getString("id"), "url", url.getScheme() + "://" + url.getRawAuthority() + base + path);
    }
}
