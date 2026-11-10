package org.jeecg.modules.packagemanagement.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/package-admin")
public class PackageAdminController {
    private final PackageAdminRegistry registry;
    private final PackageAdminSupport guard;
    private final ObjectMapper json;
    private final org.jeecg.modules.packagemanagement.application.apps.support.AppManagerFieldPermissionPolicy fields=new org.jeecg.modules.packagemanagement.application.apps.support.AppManagerFieldPermissionPolicy();
    private void mask(Object row){if(row instanceof org.jeecg.modules.packagemanagement.domain.app.AppManager app)fields.mask(app);}
    public PackageAdminController(PackageAdminRegistry registry,PackageAdminSupport guard) {
        this.registry=registry; this.guard=guard; this.json=org.jeecg.modules.packagemanagement.controller.PackageJson.mapper();
    }
    @SuppressWarnings("unchecked") private ManagedResource<Object> resource(String domain) {
        return (ManagedResource<Object>) registry.get(domain);
    }
    private Object filter(ManagedResource<Object> resource,HttpServletRequest request) {
        try {
            Object value=resource.type().getDeclaredConstructor().newInstance();
            var bean=new BeanWrapperImpl(value);
            bean.registerCustomEditor(java.util.Date.class,new org.springframework.beans.propertyeditors.CustomDateEditor(org.jeecg.modules.packagemanagement.controller.PackageJson.dateFormat(),true));
            request.getParameterMap().forEach((name,values)->{
                if(!name.equals("pageNo")&&!name.equals("pageSize")&&bean.isWritableProperty(name)&&values.length>0)
                    bean.setPropertyValue(name,landingDate(resource,name,values[0]));
            });
            return value;
        } catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
    }
    private Object body(ManagedResource<Object> resource,Map<String,Object> data) {
        var normalized=new LinkedHashMap<>(data);
        if(normalized.containsKey("time"))normalized.put("time",landingDate(resource,"time",normalized.get("time")));
        return json.convertValue(normalized,resource.type());
    }
    /** A calendar day from the landing-count picker has local midnight wire semantics. */
    private Object landingDate(ManagedResource<Object> resource,String field,Object value) {
        if("pkg_load_count".equals(resource.table())&&"time".equals(field)&&value instanceof String day
                &&day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            try {java.time.LocalDate.parse(day);}
            catch(java.time.format.DateTimeParseException e) {throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(400,"日期无效");}
            return day+" 00:00:00";
        }
        return value;
    }
    @GetMapping("/{domain}/list")
    public Result<Map<String,Object>> list(@PathVariable String domain,@RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="10") int pageSize,HttpServletRequest request) {
        PackageActor actor=guard.actor(domain,"query");
        var resource=resource(domain);
        var rows=resource.scoped(actor,filter(resource,request));
        var page=guard.page(actor,resource.table(),rows,
                row->new BeanWrapperImpl(row).getPropertyValue(resource.table().equals("pkg_app_event")?"eventId":resource.table().equals("pkg_review")?"reviewsId":"id") instanceof Number id?id.longValue():null,
                pageNo,pageSize);
        rows.forEach(this::mask);
        return Result.ok(page);
    }
    @PostMapping("/{domain}/export")
    public void export(@PathVariable String domain,HttpServletRequest request,HttpServletResponse response) throws IOException {
        if("store".equals(domain))throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(404,"源管理域不提供导出");
        PackageActor actor=guard.actor(domain,"export");
        var resource=resource(domain); resource.export(actor,filter(resource,request),response,this::mask);
    }
    @GetMapping("/{domain}/{id:[0-9]+}")
    public Result<Object> detail(@PathVariable String domain,@PathVariable long id) {
        PackageActor actor=guard.actor(domain,"query");
        Object row=resource(domain).detail(actor,id);mask(row);return Result.ok(row);
    }
    @PostMapping("/{domain}")
    public Result<Object> add(@PathVariable String domain,@RequestBody Map<String,Object> data) {
        PackageActor actor=guard.actor(domain,"add");
        var resource=resource(domain); Object row=resource.add(actor,body(resource,data));mask(row);return Result.ok(row);
    }
    @PutMapping("/{domain}/{id:[0-9]+}")
    public Result<Object> edit(@PathVariable String domain,@PathVariable long id,@RequestBody Map<String,Object> data) {
        PackageActor actor=guard.actor(domain,"edit");
        var resource=resource(domain);
        Object submitted=body(resource,data);
        Object row="apps".equals(domain)?resource.edit(actor,id,submitted,(requested,stored)->{
            fields.check((org.jeecg.modules.packagemanagement.domain.app.AppManager)requested,(org.jeecg.modules.packagemanagement.domain.app.AppManager)stored);
        }):resource.edit(actor,id,submitted);
        mask(row);return Result.ok(row);
    }
    @DeleteMapping("/{domain}/{ids}")
    public Result<Integer> delete(@PathVariable String domain,@PathVariable String ids) {
        PackageActor actor=guard.actor(domain,"remove");
        List<Long> keys=Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
        return Result.ok(resource(domain).delete(actor,keys));
    }
}
