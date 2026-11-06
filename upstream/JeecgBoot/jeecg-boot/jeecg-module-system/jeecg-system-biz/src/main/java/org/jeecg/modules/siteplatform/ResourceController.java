package org.jeecg.modules.siteplatform;

import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/site-platform/sites")
public class ResourceController {
    private final ResourceService service;
    public ResourceController(ResourceService service) { this.service = service; }

    @ExceptionHandler(ResourceFailure.class)
    public ResponseEntity<Result<?>> failure(ResourceFailure failure) {
        return ResponseEntity.status(failure.status()).body(Result.error(failure.status(), failure.getMessage()));
    }

    @GetMapping
    @RequiresPermissions("siteplatform:read")
    public Result<Map<String, Object>> sites(@RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int pageSize) {
        return Result.OK(service.sites(pageNo, pageSize));
    }

    @PostMapping
    @RequiresPermissions("siteplatform:create")
    public Result<String> create(@Valid @RequestBody ResourceRequests.SiteInput input) { return Result.OK("创建成功", service.createSite(input)); }

    @PutMapping("/{site}")
    @RequiresPermissions("siteplatform:write")
    public Result<?> update(@PathVariable String site, @Valid @RequestBody ResourceRequests.SiteInput input) { service.updateSite(site, input); return Result.OK("保存成功"); }

    @GetMapping("/{site}/targets")
    @RequiresPermissions("siteplatform:read")
    public Result<List<Map<String, Object>>> targets(@PathVariable String site) { return Result.OK(service.targets(site)); }

    @PostMapping("/{site}/targets")
    @RequiresPermissions("siteplatform:write")
    public Result<String> addTarget(@PathVariable String site, @Valid @RequestBody ResourceRequests.TargetInput input) { return Result.OK("创建成功", service.addTarget(site, input)); }

    @PutMapping("/{site}/targets/{id}")
    @RequiresPermissions("siteplatform:write")
    public Result<?> updateTarget(@PathVariable String site, @PathVariable String id, @Valid @RequestBody ResourceRequests.TargetInput input) { service.updateTarget(site, id, input); return Result.OK("保存成功"); }

    @DeleteMapping("/{site}/targets/{id}")
    @RequiresPermissions("siteplatform:write")
    public Result<?> deleteTarget(@PathVariable String site, @PathVariable String id) { service.deleteTarget(site, id); return Result.OK("删除成功"); }

    @GetMapping("/{site}/domains")
    @RequiresPermissions("siteplatform:read")
    public Result<List<Map<String, Object>>> domains(@PathVariable String site) { return Result.OK(service.domains(site)); }

    @PostMapping("/{site}/domains")
    @RequiresPermissions("siteplatform:write")
    public Result<String> bindDomain(@PathVariable String site, @Valid @RequestBody ResourceRequests.DomainInput input) { return Result.OK("绑定成功", service.bindDomain(site, input)); }

    @GetMapping("/{site}/domains/{id}/challenge")
    @RequiresPermissions("siteplatform:write")
    public Result<Map<String, Object>> challenge(@PathVariable String site, @PathVariable String id) { return Result.OK(service.challenge(site, id)); }

    @PostMapping("/{site}/domains/{id}/challenge")
    @RequiresPermissions("siteplatform:write")
    public Result<?> renew(@PathVariable String site, @PathVariable String id) { service.renewChallenge(site, id); return Result.OK("挑战已更新"); }

    @PostMapping("/{site}/domains/{id}/verify")
    @RequiresPermissions("siteplatform:write")
    public Result<?> verify(@PathVariable String site, @PathVariable String id) { service.verifyDomain(site, id); return Result.OK("域名已验证；未修改 DNS 或切换流量"); }

    @DeleteMapping("/{site}/domains/{id}")
    @RequiresPermissions("siteplatform:write")
    public Result<?> release(@PathVariable String site, @PathVariable String id) { service.releaseDomain(site, id); return Result.OK("已解除绑定"); }

    @GetMapping("/{site}/members")
    @RequiresPermissions("siteplatform:manage")
    public Result<List<Map<String, Object>>> members(@PathVariable String site) { return Result.OK(service.members(site)); }

    @PutMapping("/{site}/members")
    @RequiresPermissions("siteplatform:manage")
    public Result<?> putMember(@PathVariable String site, @Valid @RequestBody ResourceRequests.MemberInput input) { service.putMember(site, input); return Result.OK("成员权限已保存"); }

    @DeleteMapping("/{site}/members/{user}")
    @RequiresPermissions("siteplatform:manage")
    public Result<?> removeMember(@PathVariable String site, @PathVariable String user) { service.removeMember(site, user); return Result.OK("成员权限已撤销"); }

    @GetMapping("/{site}/audit")
    @RequiresPermissions("siteplatform:read")
    public Result<List<Map<String, Object>>> audit(@PathVariable String site) { return Result.OK(service.audit(site)); }
}
