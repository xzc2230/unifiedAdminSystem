package org.jeecg.modules.packagemanagement;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.controller.admin.PackageAdminSupport;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageAdminSupportTest {
    record Row(long id) {}
    @Test void paginationCountsOnlyOwnedRowsBeforeSlicing() {
        PackageAccess access=mock(PackageAccess.class);
        PackageActor actor=new PackageActor(1,"user",7,false);
        when(access.filter(eq(actor),eq("pkg_app_manager"),anyList(),any())).thenReturn(List.of(new Row(2),new Row(3)));
        var support=new PackageAdminSupport(access);
        var page=support.page(actor,"pkg_app_manager",List.of(new Row(1),new Row(2),new Row(3)),Row::id,2,1);
        assertEquals(2L,page.get("total"));
        assertEquals(List.of(new Row(3)),page.get("records"));
    }
}
