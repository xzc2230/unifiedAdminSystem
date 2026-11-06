package org.jeecg.modules.analytics;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.jeecg.common.api.vo.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** Global aggregates are deliberately restricted to platform administrators. */
@RestController
@RequestMapping("/openapi/statistics")
@RequiresRoles("admin")
@RequiresPermissions("openapi:open_api_log:list")
public class AnalyticsController {
    private final AnalyticsService service;
    public AnalyticsController(AnalyticsService service) { this.service = service; }

    @GetMapping("/status") public Result<Map<String, Object>> status() { return Result.OK(service.status()); }

    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary(@RequestParam long start, @RequestParam long end,
                                                    @RequestParam(defaultValue = "") String apiId) throws Exception {
        return Result.OK(service.summary(start, end, apiId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> invalid() { return ResponseEntity.badRequest().body(Result.error(400, "统计查询参数无效，时间区间最长24小时")); }

    @ExceptionHandler(org.apache.shiro.authz.AuthorizationException.class)
    public ResponseEntity<Result<?>> forbidden() { return ResponseEntity.status(403).body(Result.error(403, "无统计查询权限")); }

    @ExceptionHandler(org.apache.shiro.authz.UnauthenticatedException.class)
    public ResponseEntity<Result<?>> unauthenticated() { return ResponseEntity.status(401).body(Result.error(401, "请先登录")); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> unavailable(Exception error) {
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        return ResponseEntity.status(503).body(Result.error(503, "统计服务暂不可用，业务接口不受统计写入影响"));
    }
}
