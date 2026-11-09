package org.jeecg.modules.packagemanagement.controller.admin;

import java.util.*;
import java.util.function.Function;
import org.apache.shiro.SecurityUtils;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;

@org.springframework.stereotype.Component
public class PackageAdminSupport {
    private final PackageAccess access;
    public PackageAdminSupport(PackageAccess access) { this.access=access; }
    public PackageActor actor(String domain,String action) {
        PackageActor actor=access.current();
        if(!SecurityUtils.getSubject().isPermitted("package:"+domain+":"+action))
            throw new PackageFailure(403,"缺少权限: package:"+domain+":"+action);
        return actor;
    }
    public <T> Map<String,Object> page(PackageActor actor,String table,List<T> rows,
            Function<T,Long> id,int pageNo,int pageSize) {
        List<T> filtered=access.filter(actor,table,rows,id);
        int page=Math.max(1,pageNo), size=Math.max(1,Math.min(pageSize,500));
        long start=(long)(page-1)*size;
        List<T> window=start>=filtered.size()?List.of():filtered.subList((int)start,Math.min(filtered.size(),(int)start+size));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("records",window); result.put("total",(long)filtered.size());
        return result;
    }
}
