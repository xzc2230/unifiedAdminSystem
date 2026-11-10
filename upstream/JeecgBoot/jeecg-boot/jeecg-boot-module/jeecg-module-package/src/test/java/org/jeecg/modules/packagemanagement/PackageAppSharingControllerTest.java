package org.jeecg.modules.packagemanagement;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.jeecg.modules.packagemanagement.controller.admin.PackageAdminSupport;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.springframework.web.bind.annotation.*;

class PackageAppSharingControllerTest {
    @Test void allFourRoutesUseTransferPermissionAndPreserveContract() throws Exception {
        Class<?> serviceType=Class.forName("org.jeecg.modules.packagemanagement.application.sharing.PackageAppSharingService");
        Class<?> type=Class.forName("org.jeecg.modules.packagemanagement.controller.admin.sharing.PackageAppSharingController");
        Class<?> requestType=Class.forName(type.getName()+"$ShareRequest");
        Object service=mock(serviceType);
        var guard=mock(PackageAdminSupport.class);
        var actor=new PackageActor(71,"owner",7101L,false);
        when(guard.actor("apps","transfer")).thenReturn(actor);
        Object controller=type.getConstructor(PackageAdminSupport.class,serviceType).newInstance(guard,service);
        Object request=requestType.getConstructor(List.class,String.class,String.class).newInstance(List.of(1L),"target","why");
        assertArrayEquals(new String[]{"/package-admin/apps"},type.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[]{"/shares"},type.getMethod("grant",requestType).getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/shares/revoke"},type.getMethod("revoke",requestType).getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/shares"},type.getMethod("shares",long.class).getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/share-audit"},type.getMethod("audit",long.class).getAnnotation(GetMapping.class).value());
        type.getMethod("grant",requestType).invoke(controller,request);
        serviceType.getMethod("grant",PackageActor.class,Collection.class,String.class,String.class).invoke(verify(service),actor,List.of(1L),"target","why");
        type.getMethod("revoke",requestType).invoke(controller,request);
        serviceType.getMethod("revoke",PackageActor.class,Collection.class,String.class,String.class).invoke(verify(service),actor,List.of(1L),"target","why");
        type.getMethod("shares",long.class).invoke(controller,1L);
        serviceType.getMethod("shares",PackageActor.class,long.class).invoke(verify(service),actor,1L);
        type.getMethod("audit",long.class).invoke(controller,1L);
        serviceType.getMethod("audit",PackageActor.class,long.class).invoke(verify(service),actor,1L);
        verify(guard,times(4)).actor("apps","transfer");
    }
}
