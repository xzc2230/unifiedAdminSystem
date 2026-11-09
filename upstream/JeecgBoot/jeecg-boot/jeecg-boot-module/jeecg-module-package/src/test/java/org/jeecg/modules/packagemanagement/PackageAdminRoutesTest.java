package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.controller.admin.PackageAdminController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class PackageAdminRoutesTest {
    @Test void everyDomainHasListRoute() throws Exception {
        var registry=mock(org.jeecg.modules.packagemanagement.controller.admin.PackageAdminRegistry.class);
        var support=mock(org.jeecg.modules.packagemanagement.controller.admin.PackageAdminSupport.class);
        var resource=mock(org.jeecg.modules.packagemanagement.controller.admin.ManagedResource.class);
        when(resource.type()).thenReturn((Class)org.jeecg.modules.packagemanagement.domain.app.AppManager.class);
        when(resource.table()).thenReturn("pkg_app_manager");
        when(resource.scoped(any(),any())).thenReturn(java.util.List.of());
        when(registry.get(anyString())).thenReturn(resource);
        when(support.page(any(),anyString(),anyList(),any(),anyInt(),anyInt())).thenReturn(java.util.Map.of("records",java.util.List.of(),"total",0L));
        var mvc=MockMvcBuilders.standaloneSetup(new PackageAdminController(registry,support)).build();
        for(String domain:java.util.List.of("apps","store","statistics","event-configs","event-history",
                "event-errors","pwa","landing-counts","app-events","reviews","notifications")) {
            assertNotEquals(404,mvc.perform(get("/package-admin/"+domain+"/list")).andReturn().getResponse().getStatus());
        }
    }
}
