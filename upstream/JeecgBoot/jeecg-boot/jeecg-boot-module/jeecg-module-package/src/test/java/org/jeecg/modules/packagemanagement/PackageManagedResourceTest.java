package org.jeecg.modules.packagemanagement;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.controller.admin.ManagedResource;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageManagedResourceTest {
    static class Row { private Long id; private String value; private Long userId;
        public Long getId(){return id;} public void setId(Long id){this.id=id;}
        public String getValue(){return value;} public void setValue(String value){this.value=value;}
        public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}}
    @Test void batchDeletionChecksOwnershipBeforeAnyWrite() {
        PackageAccess access=mock(PackageAccess.class);
        TransactionTemplate tx=mock(TransactionTemplate.class);
        var actor=new PackageActor(1,"u",7,false);
        var deleted=new ArrayList<Long>();
        var resource=new ManagedResource<>("pkg_app_manager",Row.class,Row::getId,filter->List.of(),id->null,
                row->1,row->1,ids->{deleted.addAll(Arrays.asList(ids));return ids.length;},
                (row,a)->row.setUserId(a.legacyUserId()),List.of("value"),Map.of("value","值"),access,tx);
        doThrow(new SecurityException()).when(access).requireRows(actor,"pkg_app_manager",List.of(1L,2L));
        assertThrows(SecurityException.class,()->resource.delete(actor,List.of(1L,2L)));
        assertTrue(deleted.isEmpty());
    }
}
