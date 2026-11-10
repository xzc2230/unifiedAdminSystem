package org.jeecg.modules.packagemanagement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackageDevMigrationTest {
 @Test void fieldPolicyMustExistBeforeAnyAdminGrantCanBeEnforced() {
  assertDoesNotThrow(()->Class.forName("org.jeecg.modules.packagemanagement.application.apps.support.AppManagerFieldPermissionPolicy"));
 }
 @Test void riskManagementMustHaveItsOwnAuthorizedBoundary() {
  assertDoesNotThrow(()->Class.forName("org.jeecg.modules.packagemanagement.controller.admin.PackageRiskController"));
 }
 @Test void retainedTimezoneAndSimSettingsMustHavePersistenceFieldsButRemainInactive(){
  for(String property:java.util.List.of("systemTimezone","whetherTimezoneRestrict","simCountry","whetherSimCountryRestrict"))
   assertTrue(new org.springframework.beans.BeanWrapperImpl(new org.jeecg.modules.packagemanagement.domain.app.AppManager()).isWritableProperty(property),property);
 }
 @Test void networkFailureMustNotBecomeAnOfflineStoreState() {
  var http=org.mockito.Mockito.mock(org.jeecg.modules.packagemanagement.application.port.HttpPort.class);
  org.mockito.Mockito.when(http.exchange(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.anyMap())).thenThrow(new IllegalStateException("timeout"));
  var probe=new org.jeecg.modules.packagemanagement.config.PackageManagementConfiguration().packageStoreProbe(http);
  assertThrows(IllegalStateException.class,()->probe.getStatus("https://example.test/store"));
 }
}
