package org.jeecg.modules.packagemanagement.controller.admin.openapi;
import java.util.Map;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.application.openapi.OpenApiCallLogService;
import org.jeecg.modules.packagemanagement.controller.admin.PackageAdminSupport;
import org.jeecg.modules.packagemanagement.domain.openapi.OpenApiCallLog;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/package-admin/openapi/calllog")
public class PackageOpenApiCallLogController {
 private final OpenApiCallLogService logs;private final PackageAdminSupport guard;
 public PackageOpenApiCallLogController(OpenApiCallLogService logs,PackageAdminSupport guard){this.logs=logs;this.guard=guard;}
 @GetMapping("/list") public Result<Map<String,Object>> list(OpenApiCallLog query,@RequestParam(name="pageNo",defaultValue="1")int pageNo,@RequestParam(name="pageSize",defaultValue="10")int pageSize){return Result.ok(PackageOpenApiTokenController.page(logs.list(guard.actor("openapi-calllog","query"),query),pageNo,pageSize));}
}
