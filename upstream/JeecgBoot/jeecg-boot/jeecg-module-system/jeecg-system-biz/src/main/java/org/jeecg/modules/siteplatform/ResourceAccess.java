package org.jeecg.modules.siteplatform;

import org.apache.shiro.SecurityUtils;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.system.vo.LoginUser;
import org.springframework.stereotype.Component;

@Component
public class ResourceAccess {
    public record Actor(int tenantId, String userId) {}
    private final ResourceRepository repository;
    public ResourceAccess(ResourceRepository repository) { this.repository = repository; }

    public Actor current() {
        Object principal = SecurityUtils.getSubject().getPrincipal();
        if (!(principal instanceof LoginUser user)) throw new ResourceFailure(401, "请先登录");
        return resolve(user.getId(), TenantContext.getTenant());
    }

    Actor resolve(String userId, String selectedTenant) {
        if (userId == null || userId.isBlank() || selectedTenant == null || !selectedTenant.matches("[1-9][0-9]{0,8}")) {
            throw new ResourceFailure(403, "请选择已加入的有效租户");
        }
        int tenantId = Integer.parseInt(selectedTenant);
        if (!repository.activeMember(tenantId, userId)) throw new ResourceFailure(403, "租户成员关系无效或已过期");
        return new Actor(tenantId, userId);
    }
}
