package org.jeecg.modules.packagemanagement.controller.admin.openapi;
import java.util.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.application.openapi.OpenApiTokenService;
import org.jeecg.modules.packagemanagement.controller.admin.PackageAdminSupport;
import org.jeecg.modules.packagemanagement.domain.openapi.OpenApiToken;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/package-admin/openapi/token")
public class PackageOpenApiTokenController {
 private final OpenApiTokenService tokens;private final PackageAdminSupport guard;
 public PackageOpenApiTokenController(OpenApiTokenService tokens,PackageAdminSupport guard){this.tokens=tokens;this.guard=guard;}
 @GetMapping("/list") public Result<Map<String,Object>> list(OpenApiToken query,@RequestParam(name="pageNo",defaultValue="1")int pageNo,@RequestParam(name="pageSize",defaultValue="10")int pageSize){return Result.ok(page(tokens.list(guard.actor("openapi-token","query"),query),pageNo,pageSize));}
 @GetMapping("/{id}") public Result<OpenApiToken> detail(@PathVariable("id") long id){return Result.ok(tokens.detail(guard.actor("openapi-token","query"),id));}
 @org.jeecg.common.aspect.annotation.AutoLog("应用包OpenAPI Token新增")
 @PostMapping public Result<OpenApiToken> add(@RequestBody OpenApiToken row){return Result.ok(tokens.create(guard.actor("openapi-token","add"),row));}
 @org.jeecg.common.aspect.annotation.AutoLog("应用包OpenAPI Token修改")
 @PutMapping public Result<Integer> edit(@RequestBody OpenApiToken row){return Result.ok(tokens.update(guard.actor("openapi-token","edit"),row));}
 @org.jeecg.common.aspect.annotation.AutoLog("应用包OpenAPI Token删除")
 @DeleteMapping("/{ids}") public Result<Integer> delete(@PathVariable("ids") Long[] ids){return Result.ok(tokens.delete(guard.actor("openapi-token","remove"),Arrays.asList(ids)));}
 public static <T> Map<String,Object> page(List<T> rows,int pageNo,int pageSize){int size=Math.max(1,Math.min(pageSize,500));long start=(long)(Math.max(1,pageNo)-1)*size;return Map.of("records",start>=rows.size()?List.of():rows.subList((int)start,Math.min(rows.size(),(int)start+size)),"total",(long)rows.size());}
}
