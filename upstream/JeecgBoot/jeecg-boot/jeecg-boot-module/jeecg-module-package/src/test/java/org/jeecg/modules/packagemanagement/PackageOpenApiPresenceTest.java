package org.jeecg.modules.packagemanagement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackageOpenApiPresenceTest {
 @Test void publicManagerAndDedicatedTokenBoundaryAreAvailable() {
  assertNotNull(load("controller.openapi.OpenApiManagerController"),"missing public OpenAPI business controller");
  assertNotNull(load("application.openapi.OpenApiTokenService"),"missing dedicated Token validator");
  assertNotNull(load("config.OpenApiWebConfiguration"),"missing host authentication integration");
 }
 private Class<?> load(String name) { try{return Class.forName("org.jeecg.modules.packagemanagement."+name);}catch(ClassNotFoundException e){return null;} }
}
