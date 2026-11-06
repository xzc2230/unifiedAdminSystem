package org.jeecg.modules.siteplatform;

import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/site-platform/sites/{site}/routing")
public class RoutingController {
    private final RoutingService service;
    public RoutingController(RoutingService service) { this.service = service; }
    @ExceptionHandler(ResourceFailure.class)
    public ResponseEntity<Result<?>> failure(ResourceFailure failure) {
        return ResponseEntity.status(failure.status()).body(Result.error(failure.status(), failure.getMessage()));
    }
    @ExceptionHandler(org.apache.shiro.authz.AuthorizationException.class)
    public ResponseEntity<Result<?>> forbidden() {
        return ResponseEntity.status(403).body(Result.error(403, "资源不可访问或无操作权限"));
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Result<?>> malformed() {
        return ResponseEntity.badRequest().body(Result.error(400, "请求格式无效"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> unexpected(Exception failure) {
        return ResponseEntity.status(503).body(Result.error(503, "路由服务暂不可用，请稍后重试"));
    }
    @GetMapping
    @RequiresPermissions("siteplatform:read")
    public Result<Map<String,Object>> view(@PathVariable String site) { return Result.OK(service.view(site)); }
    @PutMapping("/draft")
    @RequiresPermissions("siteplatform:write")
    public Result<Map<String,Long>> save(@PathVariable String site, @Valid @RequestBody RoutingRequests.DraftInput input) {
        return Result.OK(Map.of("revision", service.save(site, input)));
    }
    @PostMapping("/simulate")
    @RequiresPermissions("siteplatform:read")
    public Result<Map<String,Object>> simulate(@PathVariable String site, @Valid @RequestBody RoutingRequests.SimulateInput input) {
        return Result.OK(service.simulate(site, input));
    }
    @PostMapping("/submit")
    @RequiresPermissions("siteplatform:write")
    public Result<Map<String,Long>> submit(@PathVariable String site, @Valid @RequestBody RoutingRequests.RevisionInput input) {
        return Result.OK(Map.of("revision", service.submit(site, input.revision())));
    }
    @PostMapping("/approve")
    @RequiresPermissions("siteplatform:approve")
    public Result<Map<String,Long>> approve(@PathVariable String site, @Valid @RequestBody RoutingRequests.RevisionInput input) {
        return Result.OK(Map.of("revision", service.approve(site, input.revision())));
    }
    @PostMapping("/publish")
    @RequiresPermissions("siteplatform:publish")
    public Result<Map<String,Long>> publish(@PathVariable String site, @Valid @RequestBody RoutingRequests.RevisionInput input) {
        return Result.OK(Map.of("revision", service.publish(site, input.revision())));
    }
    @PostMapping("/rollback/{releaseId}")
    @RequiresPermissions("siteplatform:publish")
    public Result<Map<String,Long>> rollback(@PathVariable String site, @PathVariable String releaseId, @Valid @RequestBody RoutingRequests.RevisionInput input) {
        return Result.OK(Map.of("revision", service.rollback(site, releaseId, input.revision())));
    }
}
