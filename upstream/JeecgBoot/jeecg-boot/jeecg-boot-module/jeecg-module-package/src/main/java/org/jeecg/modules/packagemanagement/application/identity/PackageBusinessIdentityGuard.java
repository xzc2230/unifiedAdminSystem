package org.jeecg.modules.packagemanagement.application.identity;

import java.util.Set;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Platform configuration authority is independent of business access and tenant context. */
@Component
public class PackageBusinessIdentityGuard {
    public record Operator(String userId, String username) {}
    private final JdbcTemplate jdbc;

    public PackageBusinessIdentityGuard(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Operator require(String action) {
        Subject subject=SecurityUtils.getSubject();
        if (!(subject.getPrincipal() instanceof LoginUser user) || user.getId()==null || user.getId().isBlank())
            throw new PackageFailure(401,"请先登录");
        if (!Set.of("query","open","bind","status").contains(action)
                || !subject.hasRole("admin") || !subject.isPermitted("package:identity:"+action))
            throw new PackageFailure(403,"需要平台管理员角色及对应业务账号配置权限");
        // Recheck persisted operator and role: cached Shiro principals must not revive disabled users or revoked roles.
        Long active=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u WHERE u.id=? AND u.status=1 AND u.del_flag=0"
                +" AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id"
                +" WHERE ur.user_id=u.id AND r.role_code='admin')",Long.class,user.getId());
        if (active==null || active!=1) throw new PackageFailure(403,"管理员账号或角色已失效");
        return new Operator(user.getId(),user.getUsername());
    }
}
