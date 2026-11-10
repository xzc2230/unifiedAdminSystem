package org.jeecg.modules.packagemanagement.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.function.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit per-domain binding for the source CRUD services. */
public final class ManagedResource<T> {
    private final String table;
    private final Class<T> type;
    private final Function<T,Long> id;
    private final Function<T,List<T>> list;
    private final Function<Long,T> find;
    private final ToIntFunction<T> insert,update;
    private final ToIntFunction<Long[]> remove;
    private final BiConsumer<T,PackageActor> owner;
    private final List<String> editable;
    private final Map<String,String> columns;
    private final PackageAccess access;
    private final TransactionTemplate tx;
    @FunctionalInterface public interface Lifecycle<T> {
        Runnable prepare(PackageActor actor,T value,T existing);
    }
    private Lifecycle<T> lifecycle=(actor,value,existing)->()->{};
    private Consumer<T> redactor=value->{};
    private BiConsumer<T,PackageActor> createdOwner;
    public ManagedResource<T> createdOwner(BiConsumer<T,PackageActor> claim){this.createdOwner=claim;return this;}
    public ManagedResource<T> lifecycle(Lifecycle<T> hook){this.lifecycle=hook;return this;}
    public ManagedResource<T> redact(Consumer<T> hook){this.redactor=hook;return this;}

    public ManagedResource(String table,Class<T> type,Function<T,Long> id,Function<T,List<T>> list,
            Function<Long,T> find,ToIntFunction<T> insert,ToIntFunction<T> update,
            ToIntFunction<Long[]> remove,BiConsumer<T,PackageActor> owner,List<String> editable,
            Map<String,String> columns,PackageAccess access,TransactionTemplate tx) {
        this.table=table; this.type=type; this.id=id; this.list=list; this.find=find;
        this.insert=insert; this.update=update; this.remove=remove; this.owner=owner;
        this.editable=editable; this.columns=columns; this.access=access; this.tx=tx;
    }
    public Class<T> type(){return type;}
    public String table(){return table;}
    public List<T> scoped(PackageActor actor,T filter) {
        List<T> all=list.apply(filter);
        List<T> rows=access.filter(actor,table,all==null?List.of():all,id);
        rows.forEach(redactor);
        return rows;
    }
    public T detail(PackageActor actor,long key) {
        access.requireRow(actor,table,key);
        T row=find.apply(key);
        if(row==null)throw new PackageFailure(404,"记录不存在");
        redactor.accept(row);
        return row;
    }
    public T add(PackageActor actor,T submitted) {
        return tx.execute(status->{
            T fresh=fresh(); copyAllowed(submitted,fresh);
            owner.accept(fresh,actor);
            Runnable after=lifecycle.prepare(actor,fresh,null);
            if(insert.applyAsInt(fresh)<=0 || id.apply(fresh)==null)
                throw new PackageFailure(400,"新增失败");
            if(createdOwner==null)access.claimCreated(actor,table,id.apply(fresh));else createdOwner.accept(fresh,actor);
            after.run();
            redactor.accept(fresh);
            return fresh;
        });
    }
    public T edit(PackageActor actor,long key,T submitted) {
        return edit(actor,key,submitted,(value,old)->{});
    }
    public T edit(PackageActor actor,long key,T submitted,BiConsumer<T,T> validator) {
        access.requireRow(actor,table,key);
        return tx.execute(status->{
            access.lockRows(actor,table,List.of(key));
            T existing=find.apply(key);
            if(existing==null) throw new PackageFailure(404,"记录不存在");
            validator.accept(submitted,existing);
            T previous=fresh();
            org.springframework.beans.BeanUtils.copyProperties(existing,previous);
            copyAllowed(submitted,existing);
            Runnable after=lifecycle.prepare(actor,existing,previous);
            if(update.applyAsInt(existing)<=0) throw new PackageFailure(400,"修改失败");
            after.run();
            redactor.accept(existing);
            return existing;
        });
    }
    public int delete(PackageActor actor,List<Long> ids) {
        access.requireRows(actor,table,ids);
        return tx.execute(status->{
            access.lockRows(actor,table,ids);
            int affected=remove.applyAsInt(ids.toArray(Long[]::new));
            if(affected<=0) throw new PackageFailure(400,"删除失败");
            access.removeOwners(actor,table,ids);
            return affected;
        });
    }
    private T fresh() {
        try {return type.getDeclaredConstructor().newInstance();}
        catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
    }
    private void copyAllowed(T from,T to) {
        BeanWrapperImpl source=new BeanWrapperImpl(from), destination=new BeanWrapperImpl(to);
        for(String property:editable) if(source.isReadableProperty(property)&&destination.isWritableProperty(property)
                &&source.getPropertyValue(property)!=null)
            destination.setPropertyValue(property,source.getPropertyValue(property));
    }
    public void export(PackageActor actor,T filter,HttpServletResponse response) throws IOException {
        export(actor,filter,response,row->{});
    }
    public void export(PackageActor actor,T filter,HttpServletResponse response,Consumer<T> mask) throws IOException {
        List<T> rows=scoped(actor,filter);
        rows.forEach(mask);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition","attachment; filename="+table+".xlsx");
        try(XSSFWorkbook book=new XSSFWorkbook()) {
            var sheet=book.createSheet("数据");
            var header=sheet.createRow(0); int c=0;
            for(String label:columns.values()) header.createCell(c++).setCellValue(label);
            int r=1;
            for(T item:rows) {
                var line=sheet.createRow(r++); var values=new BeanWrapperImpl(item); c=0;
                for(String property:columns.keySet()) {
                    Object value=values.getPropertyValue(property);
                    var cell=line.createCell(c++);
                    if(value instanceof Number number)cell.setCellValue(number.doubleValue());
                    else cell.setCellValue(value==null?"":value instanceof Date date?org.jeecg.modules.packagemanagement.controller.PackageJson.dateFormat().format(date):String.valueOf(value));
                }
            }
            book.write(response.getOutputStream());
        }
    }
}
