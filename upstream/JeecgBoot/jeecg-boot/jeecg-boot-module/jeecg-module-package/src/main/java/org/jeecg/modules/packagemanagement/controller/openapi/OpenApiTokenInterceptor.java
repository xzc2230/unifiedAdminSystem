package org.jeecg.modules.packagemanagement.controller.openapi;
import jakarta.servlet.http.*;
import org.jeecg.modules.packagemanagement.application.openapi.*;
import org.jeecg.modules.packagemanagement.domain.openapi.OpenApiToken;
import org.springframework.web.servlet.HandlerInterceptor;
public final class OpenApiTokenInterceptor implements HandlerInterceptor {
 private final OpenApiTokenService tokens;
 public OpenApiTokenInterceptor(OpenApiTokenService tokens,OpenApiCallLogService logs){this.tokens=tokens;}
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception {
  String raw=request.getHeader("X-Api-Token");OpenApiToken token=tokens.valid(raw);
  OpenApiToken known=token==null?tokens.auditCandidate(raw):token;
  if(known!=null)request.setAttribute(OpenApiAuth.ATTRIBUTE+".auditToken",known);
  request.setAttribute(OpenApiAuth.ATTRIBUTE+".classification",token!=null?"accepted":raw==null||raw.isBlank()?"missing_token":known==null?"unknown_token":"disabled_expired_or_inactive_token");
  if(token==null){String msg=raw==null||raw.isBlank()?"缺少 Header X-Api-Token":"Token 无效、已停用或已过期";
   String body=OpenApiManagerController.json(OpenApiManagerController.error(401,msg));response.setStatus(401);response.setCharacterEncoding("UTF-8");response.setContentType("application/json;charset=UTF-8");response.getWriter().write(body);return false;
  }
  request.setAttribute(OpenApiAuth.ATTRIBUTE,token);return true;
 }
}
