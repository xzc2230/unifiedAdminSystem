package org.jeecg.modules.packagemanagement.controller.admin;

import java.math.*;
import java.util.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;
import org.jeecg.modules.packagemanagement.domain.statistics.AppStatistics;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/package-admin")
public class PackageAdminActionsController {
    private final PackageAdminRegistry registry;
    private final PackageAdminSupport guard;
    private final PackageAccess access;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    public PackageAdminActionsController(PackageAdminRegistry registry,PackageAdminSupport guard,
            PackageAccess access,TransactionTemplate tx,JdbcTemplate jdbc) {
        this.registry=registry;this.guard=guard;this.access=access;this.tx=tx;this.jdbc=jdbc;
    }
    @GetMapping("/apps/transfer-targets")
    public Result<List<Map<String,Object>>> targets() {
        var actor=guard.actor("apps","transfer");
        return Result.ok(jdbc.queryForList("SELECT u.id, COALESCE(NULLIF(u.realname,''),u.username) AS displayName FROM sys_user u JOIN sys_user_tenant m ON m.user_id=u.id JOIN sys_tenant t ON t.id=m.tenant_id JOIN pkg_user_identity i ON CONVERT(i.jeecg_user_id USING utf8mb4) COLLATE utf8mb4_general_ci=CONVERT(u.id USING utf8mb4) COLLATE utf8mb4_general_ci AND i.tenant_id=m.tenant_id WHERE m.tenant_id=? AND m.status='1' AND t.status=1 AND t.del_flag=0 AND u.status=1 AND u.del_flag=0 AND (t.begin_date IS NULL OR t.begin_date<=NOW()) AND (t.end_date IS NULL OR t.end_date>NOW()) GROUP BY u.id,u.realname,u.username HAVING COUNT(DISTINCT i.legacy_user_id)=1 ORDER BY displayName",actor.tenantId()));
    }
    public record Transfer(List<Long> ids,String targetUser) {}
    @PostMapping("/apps/transfer")
    public Result<Integer> transfer(@RequestBody Transfer request) {
        var actor=guard.actor("apps","transfer");
        Integer count=tx.execute(status->{
            access.transfer(actor,"pkg_app_manager",request.ids(),request.targetUser());
            String name=jdbc.queryForObject("SELECT COALESCE(NULLIF(realname,''),username) FROM sys_user WHERE id=?",String.class,request.targetUser());
            int changed=0;
            for(Long id:request.ids())changed+=jdbc.update("UPDATE pkg_app_manager SET user_name=? WHERE id=?",name,id);
            return changed;
        });
        return Result.ok(count);
    }
    @SuppressWarnings("unchecked")
    @GetMapping("/pwa/all")
    public Result<List<AppPwa>> all(AppPwa filter) {
        var actor=guard.actor("pwa","query");
        return Result.ok(((ManagedResource<AppPwa>)registry.get("pwa")).scoped(actor,filter));
    }
    @SuppressWarnings("unchecked")
    @GetMapping("/statistics/getTotal")
    public Result<Map<String,Object>> totals(AppStatistics filter) {
        var actor=guard.actor("statistics","query");
        var rows=((ManagedResource<AppStatistics>)registry.get("statistics")).scoped(actor,filter);
        BigDecimal money=BigDecimal.ZERO.setScale(2);long payments=0,registrations=0;
        for(var row:rows) {
            if(row.getPaymentTotal()!=null)money=money.add(row.getPaymentTotal()).setScale(2,RoundingMode.HALF_UP);
            if(row.getPaymentNum()!=null)payments+=row.getPaymentNum();
            if(row.getReigisterNum()!=null)registrations+=row.getReigisterNum();
        }
        return Result.ok(Map.of("totalPackage",rows.size(),"totalPayment",payments,"totalMoney",money,"totalRegister",registrations));
    }
}
