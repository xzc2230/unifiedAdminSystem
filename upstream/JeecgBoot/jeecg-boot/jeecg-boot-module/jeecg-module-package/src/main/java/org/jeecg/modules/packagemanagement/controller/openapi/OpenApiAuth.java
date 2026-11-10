package org.jeecg.modules.packagemanagement.controller.openapi;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.modules.packagemanagement.domain.openapi.OpenApiToken;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
public final class OpenApiAuth {
 public static final String ATTRIBUTE=OpenApiAuth.class.getName()+".token";
 private OpenApiAuth(){}
 public static OpenApiToken require(HttpServletRequest request){
  Object value=request.getAttribute(ATTRIBUTE);if(!(value instanceof OpenApiToken token))throw new PackageFailure(401,"未通过OpenAPI Token校验");return token;
 }
}
