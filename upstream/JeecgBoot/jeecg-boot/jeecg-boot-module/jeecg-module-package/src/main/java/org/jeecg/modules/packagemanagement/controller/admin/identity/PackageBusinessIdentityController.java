package org.jeecg.modules.packagemanagement.controller.admin.identity;

import java.util.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.application.identity.PackageBusinessIdentityGuard;
import org.jeecg.modules.packagemanagement.application.identity.PackageBusinessIdentityService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/package-admin/identities")
public class PackageBusinessIdentityController {
    private final PackageBusinessIdentityGuard guard;
    private final PackageBusinessIdentityService service;

    public PackageBusinessIdentityController(PackageBusinessIdentityGuard guard,PackageBusinessIdentityService service) {
        this.guard=guard; this.service=service;
    }
    public record OpenRequest(Integer tenantId,String userId,String reason) {}
    // Object preserves the JSON token type; accepting Jackson's numeric-to-String coercion would lose precision.
    public record BindRequest(Integer tenantId,String userId,Object legacyUserId,String reason) {}
    public record StatusRequest(Integer tenantId,String userId,Object enabled,String reason) {}

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Result<Object> invalidHttpInput(Exception failure) {
        return Result.error(400,"请求字段或格式无效");
    }

    @GetMapping("/tenants")
    public Result<List<Map<String,Object>>> tenants() {
        guard.require("query"); return Result.ok(service.tenants());
    }
    @GetMapping("/list")
    public Result<PackageBusinessIdentityService.Page> list(
            @RequestParam(name="tenantId") Integer tenantId,
            @RequestParam(name="keyword",required=false) String keyword,
            @RequestParam(name="state",required=false) String state,
            @RequestParam(name="pageNo",defaultValue="1") int pageNo,
            @RequestParam(name="pageSize",defaultValue="10") int pageSize) {
        guard.require("query"); return Result.ok(service.list(tenantId,keyword,state,pageNo,pageSize));
    }
    @PostMapping("/open")
    public Result<Map<String,Object>> open(@RequestBody OpenRequest request) {
        var operator=guard.require("open");
        return Result.ok(service.open(operator,request.tenantId(),request.userId(),request.reason()));
    }
    @GetMapping("/legacy-preview")
    public Result<PackageBusinessIdentityService.Preview> preview(
            @RequestParam(name="tenantId") Integer tenantId,@RequestParam(name="userId") String userId,
            @RequestParam(name="legacyUserId") String legacyUserId) {
        guard.require("bind"); return Result.ok(service.preview(tenantId,userId,legacyUserId));
    }
    @PostMapping("/bind")
    public Result<Map<String,Object>> bind(@RequestBody BindRequest request) {
        var operator=guard.require("bind");
        return Result.ok(service.bind(operator,request.tenantId(),request.userId(),request.legacyUserId(),request.reason()));
    }
    @PostMapping("/status")
    public Result<Map<String,Object>> status(@RequestBody StatusRequest request) {
        var operator=guard.require("status");
        return Result.ok(service.status(operator,request.tenantId(),request.userId(),request.enabled(),request.reason()));
    }
    @GetMapping("/audit")
    public Result<PackageBusinessIdentityService.Page> audit(
            @RequestParam(name="tenantId") Integer tenantId,@RequestParam(name="userId",required=false) String userId,
            @RequestParam(name="pageNo",defaultValue="1") int pageNo,
            @RequestParam(name="pageSize",defaultValue="10") int pageSize) {
        guard.require("query"); return Result.ok(service.audit(tenantId,userId,pageNo,pageSize));
    }
}
