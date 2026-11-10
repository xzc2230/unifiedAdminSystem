package org.jeecg.modules.packagemanagement.controller.admin;
import java.util.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.application.packstat.PackStatService;
import org.jeecg.modules.packagemanagement.domain.packstat.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/package-admin/packstat")
public class PackagePackStatController {
 private final PackStatService service;private final PackageAdminSupport guard;
 public PackagePackStatController(PackStatService service,PackageAdminSupport guard){this.service=service;this.guard=guard;}
 @GetMapping("/list") public Result<Map<String,Object>> list(PackStat query,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="10") int pageSize){
  var actor=guard.actor("packstat","query");List<PackStat> all=service.list(query,actor);
  int size=Math.max(1,Math.min(500,pageSize));long offset=(long)(Math.max(1,pageNo)-1)*size;
  return Result.ok(Map.of("records",offset>=all.size()?List.of():all.subList((int)offset,Math.min(all.size(),(int)offset+size)),"total",all.size()));
 }
 @GetMapping("/authors") public Result<List<String>> authors(){return Result.ok(service.authors(guard.actor("packstat","query")));}
 @GetMapping("/regions") public Result<List<PackStatRegion>> regions(){guard.actor("packstat","query");return Result.ok(service.regions());}
 @GetMapping("/surviveSummary") public Result<Map<String,Object>> summary(@RequestParam(required=false)String packer,@RequestParam(required=false)String passMonth){return Result.ok(service.summary(packer,passMonth,guard.actor("packstat","query")));}
 @GetMapping("/{id:[0-9]+}") public Result<PackStat> detail(@PathVariable long id){return Result.ok(service.detail(id,guard.actor("packstat","query")));}
 @PostMapping public Result<Integer> add(@RequestBody PackStat row){return Result.ok(service.add(row,guard.actor("packstat","add")));}
 @PutMapping public Result<Integer> edit(@RequestBody PackStat row){return Result.ok(service.edit(row,guard.actor("packstat","edit")));}
 @DeleteMapping("/{ids}") public Result<Integer> delete(@PathVariable Long[] ids){return Result.ok(service.delete(ids,guard.actor("packstat","remove")));}
}
