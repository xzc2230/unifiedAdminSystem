package org.jeecg.modules.packagemanagement.infrastructure.identity;
import java.util.*;
import java.util.function.Function;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.transaction.annotation.Transactional;
/** Management boundary. Public compatibility queries deliberately do not use this class. */
public class PackageAccess  {
    private final PackageIdentityRepository repository;
    public PackageAccess(PackageIdentityRepository repository) {
        this.repository=repository;
    }
    public PackageActor current() {
        Object principal=SecurityUtils.getSubject().getPrincipal();
        if(!(principal instanceof LoginUser user))throw new PackageFailure(401,"请先登录");
        return resolve(user.getId(),TenantContext.getTenant(),SecurityUtils.getSubject().isPermitted("package:*"));
    }
    public PackageActor resolve(String user,String tenant,boolean administrator) {
        if(user==null||user.isBlank()||tenant==null||!tenant.matches("[1-9][0-9]{0,8}"))throw new PackageFailure(403,"请选择已加入的有效租户");
        int id=Integer.parseInt(tenant);
        if(!repository.activeMember(id,user))throw new PackageFailure(403,"租户成员关系无效或已过期");
        List<Long> legacy=repository.legacyIds(id,user);
        if(legacy==null||legacy.size()!=1)throw new PackageFailure(403,"旧用户映射缺失或不唯一");
        return new PackageActor(id,user,legacy.get(0),administrator);
    }
    private void active(PackageActor actor) {
        if(actor==null||!repository.activeMember(actor.tenantId(),actor.userId()))throw new PackageFailure(403,"租户成员关系无效或已过期");
    }
    public Set<Long> scope(PackageActor actor,String type) {
        active(actor);
        PackageIdentityRepository.validateType(type);
        return repository.ownedIds(actor,type);
    }
    public List<Long> filterIds(PackageActor actor,String type,Collection<Long> ids) {
        Set<Long> allowed=scope(actor,type);
        return ids.stream().filter(allowed::contains).toList();
    }
    public <T> List<T> filter(PackageActor actor,String type,Collection<T> rows,Function<T,Long> id) {
        Set<Long> allowed=scope(actor,type);
        return rows.stream().filter(row->allowed.contains(id.apply(row))).toList();
    }
    public void requireRow(PackageActor actor,String type,Long id) {
        requireRows(actor,type,Collections.singletonList(id));
    }
    public void requireRows(PackageActor actor,String type,Collection<Long> ids) {
        Set<Long> allowed=scope(actor,type);
        validateRows(ids,allowed);
    }
    private void validateRows(Collection<Long> ids,Set<Long> allowed) {
        if(ids==null||ids.isEmpty()||ids.stream().anyMatch(id->id==null||!allowed.contains(id)))throw new PackageFailure(403,"资源未映射或不属于当前租户和用户");
    }
    /** Call within the same transaction as the following management mutations. */
    public void lockRows(PackageActor actor,String type,Collection<Long> ids) {
        active(actor);
        validateRows(ids,repository.lockOwnedIds(actor,type));
    }
    /** Existing independently owned store/statistics rows may keep their transferred app association. */
    public void lockIndependentPackageParent(PackageActor actor,long appId) {
        active(actor);
        repository.lockTenantParent(actor,"pkg_app_manager",appId);
    }
    /** Called after an authorized standalone review move, inside its update transaction. */
    public void reassignReviewToParent(PackageActor actor,long reviewId,long parentId) {
        lockRows(actor,"pkg_review",List.of(reviewId));
        lockRows(actor,"pkg_pwa",List.of(parentId));
        repository.reassignReviewToParent(actor,reviewId,parentId);
    }
    /** Only for rows just inserted by trusted application code in the current transaction. Historical claims require verified migration. */
    public void claimCreated(PackageActor actor,String type,long id) {
        active(actor);
        repository.claimCreated(actor,type,id);
    }
    /** Newly inserted children follow the locked, verified parent's registry owner. */
    public void claimCreatedFromParent(PackageActor actor,String child,long id,String parent,long parentId) {
        lockRows(actor,parent,List.of(parentId));
        repository.claimCreatedFromParent(actor,child,id,parent,parentId);
    }
    public long effectiveLegacyUserId(PackageActor actor) {
        active(actor);
        return actor.legacyUserId();
    }
    public int effectiveLegacyUserIdInt(PackageActor actor) {
        return Math.toIntExact(effectiveLegacyUserId(actor));
    }
    public List<String> logPackages(PackageActor actor) {
        active(actor);
        return repository.ownedPackages(actor);
    }
    public void requireLogPackage(PackageActor actor,String packageName) {
        if(packageName==null||!logPackages(actor).contains(packageName))throw new PackageFailure(403,"日志包不属于当前租户和用户");
    }
    @Transactional public void transfer(PackageActor actor,String type,Collection<Long> ids,String targetUser) {
        requireRows(actor,type,ids);
        lockRows(actor,type,ids);
        if(targetUser==null||!repository.activeMember(actor.tenantId(),targetUser))throw new PackageFailure(403,"目标用户不属于当前有效租户");
        repository.transfer(actor,type,ids,targetUser);
    }
    /** Invoke only after requireRows/lockRows and successful business deletion in one transaction. */
    public void removeOwners(PackageActor actor,String type,Collection<Long> ids) {
        requireRows(actor,type,ids);
        repository.removeOwners(type,ids);
    }
}
