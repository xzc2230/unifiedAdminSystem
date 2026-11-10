package org.jeecg.modules.packagemanagement.controller.admin;

import java.util.*;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.application.pwa.IAppPwaService;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;
import org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Validates both ends of relationships before any service or external side effect. */
@Component
public class PackageResourcePolicy {
    private final PackageAccess access;
    private final IAppManagerService apps;
    private final IAppPwaService pwa;
    private final StoreStatusMapper stores;
    private final ReviewsRecordMapper reviews;
    private final JdbcTemplate jdbc;
    public PackageResourcePolicy(PackageAccess access,IAppManagerService apps,IAppPwaService pwa,
            StoreStatusMapper stores,ReviewsRecordMapper reviews,JdbcTemplate jdbc) {
        this.access=access;this.apps=apps;this.pwa=pwa;this.stores=stores;this.reviews=reviews;this.jdbc=jdbc;
    }
    public <T> void attach(ManagedResource<T> resource) {
        resource.lifecycle((actor,value,old)->prepare(actor,resource.table(),value,old));
        if(resource.type()==ReviewsRecord.class)resource.createdOwner((value,actor)->{
            var review=(ReviewsRecord)value;access.claimCreatedFromParent(actor,"pkg_review",review.getReviewsId(),"pkg_pwa",review.getAppId());
        });
        if(resource.type()==AppPwa.class) resource.redact(value->((AppPwa)value).setPrivateKey(null));
    }
    private Object property(Object bean,String name) {
        return bean==null?null:new BeanWrapperImpl(bean).getPropertyValue(name);
    }
    private Long number(Object bean,String name) {
        Object value=property(bean,name);return value instanceof Number n?n.longValue():null;
    }
    private void packageParent(PackageActor actor,Object value,String field,boolean independent) {
        if(value==null)return;
        Object code=property(value,field);
        if(code==null||String.valueOf(code).isBlank())throw new PackageFailure(400,"包标识不能为空");
        var app=apps.selectAppManagerByPackageName(String.valueOf(code));
        if(app==null)throw new PackageFailure(400,"关联应用包不存在");
        if(independent)access.lockIndependentPackageParent(actor,app.getId());
        else access.lockRows(actor,"pkg_app_manager",List.of(app.getId()));
    }
    private void pwaParent(PackageActor actor,Object value) {
        if(value==null)return;
        Long id=number(value,"appId");
        if(id==null)throw new PackageFailure(400,"关联PWA不能为空");
        access.lockRows(actor,"pkg_pwa",List.of(id));
    }
    private void countParent(PackageActor actor,Object value) {
        if(value==null)return;
        String host=(String)property(value,"hostName");
        if(host==null||host.indexOf('.')<1)throw new PackageFailure(400,"落地页域名无效");
        var filter=new AppPwa();filter.setAppHost(host.substring(0,host.indexOf('.')));
        filter.setAppSub(host.substring(host.indexOf('.')+1));
        var matches=pwa.selectAppPwaList(filter);
        if(matches==null||matches.size()!=1)throw new PackageFailure(400,"落地页未唯一匹配PWA");
        access.lockRows(actor,"pkg_pwa",List.of(matches.get(0).getId()));
    }
    private Runnable prepare(PackageActor actor,String table,Object value,Object old) {
        String packageField=switch(table) {
            case "pkg_store_status","pkg_app_statistics"->"packageCode";
            case "pkg_event_config"->"packageId";
            case "pkg_event_history"->"packageName";
            case "pkg_event_error"->"errorPackage";
            default->null;
        };
        if(packageField!=null){
            boolean independent=Set.of("pkg_store_status","pkg_app_statistics").contains(table)&&old!=null
                &&Objects.equals(property(old,packageField),property(value,packageField));
            packageParent(actor,old,packageField,independent);packageParent(actor,value,packageField,independent);
        }
        if(Set.of("pkg_review","pkg_notification","pkg_app_event").contains(table)) {
            pwaParent(actor,old);pwaParent(actor,value);
        }
        if(table.equals("pkg_load_count")){countParent(actor,old);countParent(actor,value);}
        if(table.equals("pkg_review")&&old!=null&&!Objects.equals(number(old,"appId"),number(value,"appId"))) {
            return ()->access.reassignReviewToParent(actor,number(value,"reviewsId"),number(value,"appId"));
        }
        if(table.equals("pkg_app_statistics")&&old!=null&&!Objects.equals(property(old,"packageCode"),property(value,"packageCode")))
            throw new PackageFailure(400,"统计包名不可修改");
        if(table.equals("pkg_app_manager")) {
            String rawCode=(String)property(value,"packageCode");
            String code=rawCode==null?null:rawCode.trim();
            new BeanWrapperImpl(value).setPropertyValue("packageCode",code);
            if(code==null||code.isBlank())throw new PackageFailure(400,"包名不能为空");
            if(old!=null&&!Objects.equals(property(old,"packageCode"),code))throw new PackageFailure(400,"包名不可修改");
            var before=stores.selectStoreStatusByPackageName(code);
            return ()->{
                if(before==null){var created=stores.selectStoreStatusByPackageName(code);
                    if(created!=null)access.claimCreatedStoreFromApp(actor,created.getId(),number(value,"id"));}
            };
        }
        if(value instanceof AppPwa app) {
            if(old==null)app.setCreateTime(new Date());else app.setAppChangeTime(new Date());
            if(!Set.of("0","1").contains(app.getAppType()))throw new PackageFailure(400,"应用类型无效");
            List<ReviewsRecord> nested=app.getReviewsRecords();
            if(nested==null) {nested=new ArrayList<>();app.setReviewsRecords(nested);}
            Set<Long> seen=new HashSet<>();
            for(var review:nested) {
                if(review.getReviewsId()!=null) {
                    if(old==null||!seen.add(review.getReviewsId()))throw new PackageFailure(400,"评论ID无效");
                    access.lockRows(actor,"pkg_review",List.of(review.getReviewsId()));
                    var saved=reviews.selectReviewsRecordByReviewsId(review.getReviewsId());
                    if(saved==null||!Objects.equals(saved.getAppId(),app.getId()))throw new PackageFailure(403,"评论不属于当前PWA");
                }
                review.setAppId(app.getId());
                review.setCreateBy(null);review.setUpdateBy(null);review.setUpdateTime(null);review.setRemark(null);review.setParams(null);
                if(review.getCreateTime()==null&&review.getReviewsId()==null)review.setCreateTime(new Date());
                else if(review.getCreateTime()==null)review.setCreateTime(reviews.selectReviewsRecordByReviewsId(review.getReviewsId()).getCreateTime());
            }
            List<ReviewsRecord> finalNested=nested;
            return ()->{for(var review:finalNested)if(!seen.contains(review.getReviewsId()))
                access.claimCreatedFromParent(actor,"pkg_review",review.getReviewsId(),"pkg_pwa",app.getId());};
        }
        return ()->{};
    }
}
