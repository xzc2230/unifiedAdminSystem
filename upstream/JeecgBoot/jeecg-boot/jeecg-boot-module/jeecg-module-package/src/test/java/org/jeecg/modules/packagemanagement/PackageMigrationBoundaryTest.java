package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.controller.admin.ManagedResource;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PackageMigrationBoundaryTest {
    public static class Row {
        private Long id; private String value; private Long parent;
        public Long getId(){return id;} public void setId(Long value){id=value;}
        public String getValue(){return value;} public void setValue(String value){this.value=value;}
        public Long getParent(){return parent;} public void setParent(Long value){parent=value;}
    }
    @Test void parentValidationPrecedesMutationAndAfterHookFollowsClaim() {
        var access=mock(PackageAccess.class); var tx=mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(i->((TransactionCallback<?>)i.getArgument(0)).doInTransaction(null));
        var actor=new PackageActor(1000,"u",7,false); var writes=new ArrayList<String>();
        var resource=new ManagedResource<>("pkg_review",Row.class,Row::getId,r->List.of(),id->null,
            r->{writes.add("insert");r.setId(1L);return 1;},r->1,ids->1,(r,a)->{},
            List.of("value","parent"),Map.of("value","值"),access,tx);
        resource.lifecycle((a,fresh,old)->{
            if(Objects.equals(fresh.getParent(),2L)) throw new SecurityException("foreign parent");
            return ()->writes.add("after");
        });
        var submitted=new Row(); submitted.setParent(2L);
        assertThrows(SecurityException.class,()->resource.add(actor,submitted));
        assertTrue(writes.isEmpty()); verify(access,never()).claimCreated(any(),anyString(),anyLong());
        submitted.setParent(1L); resource.add(actor,submitted);
        assertEquals(List.of("insert","after"),writes); verify(access).claimCreated(actor,"pkg_review",1L);
    }
    @Test void editKeepsExistingValuesWhenFieldsAreAbsentAndListSanitizesSecrets() {
        var access=mock(PackageAccess.class); var tx=mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(i->((TransactionCallback<?>)i.getArgument(0)).doInTransaction(null));
        var old=new Row(); old.setId(1L); old.setValue("existing");
        when(access.filter(any(),anyString(),anyList(),any())).thenAnswer(i->i.getArgument(2));
        var resource=new ManagedResource<>("pkg_review",Row.class,Row::getId,r->List.of(old),id->old,
            r->1,r->1,ids->1,(r,a)->{},List.of("value"),Map.of("value","值"),access,tx);
        var actor=new PackageActor(1000,"u",7,false);
        assertEquals("existing",resource.edit(actor,1,new Row()).getValue());
        resource.redact(r->r.setValue(null));
        assertNull(resource.scoped(actor,new Row()).get(0).getValue());
    }
}
