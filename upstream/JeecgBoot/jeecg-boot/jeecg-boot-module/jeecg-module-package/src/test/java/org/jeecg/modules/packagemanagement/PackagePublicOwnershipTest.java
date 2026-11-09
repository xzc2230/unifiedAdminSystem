package org.jeecg.modules.packagemanagement;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.application.pwa.impl.*;
import org.jeecg.modules.packagemanagement.domain.pwa.*;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackagePublicOwnership;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackagePublicOwnershipTest {
    @Test void subscriptionClaimsOnlyNewlyInsertedRows() {
        var mapper=mock(AppPwaSubMapper.class);var pwa=mock(AppPwaMapper.class);var ownership=mock(PackagePublicOwnership.class);
        var service=new AppPwaSubServiceImpl();ReflectionTestUtils.setField(service,"appPwaSubMapper",mapper);ReflectionTestUtils.setField(service,"appPwaMapper",pwa);ReflectionTestUtils.setField(service,"publicOwnership",ownership);
        var parent=new AppPwa();parent.setId(3L);when(pwa.selectAppPwaList(any())).thenReturn(List.of(parent));
        var old=new AppPwaSub();old.setId(7L);when(mapper.selectAppPwaSubList(any())).thenReturn(List.of(old));when(mapper.updateAppPwaSub(any())).thenReturn(1);
        var submitted=new AppPwaSub();submitted.setHost("app.example.invalid");submitted.setEndpoint("endpoint");submitted.setKeys(new PwaKeys());
        assertEquals(1,service.insertAppPwaSub(submitted));verifyNoInteractions(ownership);
        when(mapper.selectAppPwaSubList(any())).thenReturn(List.of());when(mapper.insertAppPwaSub(any())).thenAnswer(a->{((AppPwaSub)a.getArgument(0)).setId(8L);return 1;});
        submitted.setId(null);assertEquals(1,service.insertAppPwaSub(submitted));verify(ownership).inherit("pkg_pwa_subscription",8L,"pkg_pwa",3L);
    }
    @Test void absentPwaDetailIsNullInsteadOfNullPointer() {
        var mapper=mock(AppPwaMapper.class);var service=new AppPwaServiceImpl();ReflectionTestUtils.setField(service,"appPwaMapper",mapper);
        assertNull(service.selectAppPwaById(404L));
    }
}
