package org.jeecg.modules.packagemanagement.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.application.applog.EsAppLogServiceImpl;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/package-admin/request-logs")
public class PackageRequestLogController {
    private final EsAppLogServiceImpl logs;
    private final PackageAdminSupport guard;
    private final PackageAccess access;
    public PackageRequestLogController(EsAppLogServiceImpl logs,PackageAdminSupport guard,PackageAccess access) {
        this.logs=logs;this.guard=guard;this.access=access;
    }
    private AppLog owned(PackageActor actor,String id) {
        var row=logs.findById(id).orElseThrow(()->new PackageFailure(404,"日志不存在"));
        access.requireLogPackage(actor,row.getPackageName());return row;
    }
    @GetMapping("/list") public Result<Map<String,Object>> list(AppLog filter,
            @RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="10") int pageSize) {
        var actor=guard.actor("request-logs","query");
        var page=logs.list(filter,Math.max(1,pageNo),Math.max(1,Math.min(500,pageSize)),access.logPackages(actor));
        return Result.ok(Map.of("records",page.rows(),"total",page.total()));
    }
    @GetMapping("/{id}") public Result<AppLog> detail(@PathVariable String id) {
        return Result.ok(owned(guard.actor("request-logs","query"),id));
    }
    @PostMapping public Result<AppLog> add(@RequestBody AppLog submitted) {
        var actor=guard.actor("request-logs","add");access.requireLogPackage(actor,submitted.getPackageName());
        AppLog fresh=new AppLog();copy(submitted,fresh);return Result.ok(logs.save(fresh));
    }
    @PutMapping("/{id}") public Result<AppLog> edit(@PathVariable String id,@RequestBody AppLog submitted) {
        guard.actor("request-logs","edit");
        throw new PackageFailure(400,"请求日志不支持修改");
    }
    private void copy(AppLog from,AppLog to) {
        to.setPackageName(from.getPackageName());to.setReqPath(from.getReqPath());to.setReqIp(from.getReqIp());
        to.setReqParams(from.getReqParams());to.setReqHeards(from.getReqHeards());to.setReqMethod(from.getReqMethod());
        to.setResultMsg(from.getResultMsg());to.setErrorMsg(from.getErrorMsg());to.setReqTime(from.getReqTime());to.setIsMobile(from.getIsMobile());
    }
    @DeleteMapping("/{ids}") public Result<Integer> delete(@PathVariable String ids) {
        var actor=guard.actor("request-logs","remove");
        var keys=Arrays.stream(ids.split(",",-1)).map(String::trim).filter(key->!key.isEmpty()).distinct().toList();
        if(keys.isEmpty()) throw new PackageFailure(400,"无效日志编号");
        var packages=access.logPackages(actor);
        if(packages==null||packages.isEmpty()) throw new PackageFailure(404,"日志不存在");
        logs.deleteOwned(keys,packages);
        return Result.ok(keys.size());
    }
    @DeleteMapping("/clean") public Result<Long> clean(AppLog filter) {
        var actor=guard.actor("request-logs","clean");return Result.ok(logs.clean(filter,access.logPackages(actor)));
    }
    @PostMapping("/export") public void export(AppLog filter,HttpServletResponse response) throws IOException {
        var actor=guard.actor("request-logs","export");var rows=logs.export(filter,access.logPackages(actor));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition","attachment; filename=request-logs.xlsx");
        try(var book=new XSSFWorkbook()) {
            var sheet=book.createSheet("请求日志");String[] headers={"包名","请求地址","请求IP","请求参数","请求头","请求方式","请求结果","异常信息","请求时间","是否手机"};
            var header=sheet.createRow(0);for(int i=0;i<headers.length;i++)header.createCell(i).setCellValue(headers[i]);
            var date=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");date.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            int r=1;for(var item:rows) {
                Object[] values={item.getPackageName(),item.getReqPath(),item.getReqIp(),item.getReqParams(),item.getReqHeards(),item.getReqMethod(),item.getResultMsg(),item.getErrorMsg(),item.getReqTime()==null?null:date.format(item.getReqTime()),item.getIsMobile()};
                var row=sheet.createRow(r++);for(int c=0;c<values.length;c++)row.createCell(c).setCellValue(values[c]==null?"":values[c].toString());
            }
            book.write(response.getOutputStream());
        }
    }
}
