package org.jeecg.modules.packagemanagement.controller.openapi;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jeecg.modules.packagemanagement.application.openapi.OpenApiCallLogService;
import org.jeecg.modules.packagemanagement.domain.openapi.OpenApiToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Captures the actual wire result even when MVC fails before controller invocation. */
public final class OpenApiAuditFilter extends OncePerRequestFilter {
 private final OpenApiCallLogService logs;
 public OpenApiAuditFilter(OpenApiCallLogService logs){this.logs=logs;}
 @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=request.getRequestURI().substring(request.getContextPath().length());return !path.equals("/openapi/manager")&&!path.startsWith("/openapi/manager/");}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
  var captured=new ContentCachingRequestWrapper(request,16000);var result=new ContentCachingResponseWrapper(response);
  try{chain.doFilter(captured,result);}finally{
   try{
    // Authentication may reject before any consumer reads the body. Drain only a bounded remainder.
    if(captured.getContentAsByteArray().length==0){try{captured.getInputStream().readNBytes(16000);}catch(IOException ignored){/* MVC may already have closed the consumed stream. */}}
    var info=new LinkedHashMap<String,Object>();info.put("method",request.getMethod());info.put("query",request.getQueryString());
    info.put("body",new String(captured.getContentAsByteArray(),StandardCharsets.UTF_8));
    info.put("authentication",Objects.toString(request.getAttribute(OpenApiAuth.ATTRIBUTE+".classification"),"unresolved"));
    Object candidate=request.getAttribute(OpenApiAuth.ATTRIBUTE+".auditToken");
    OpenApiToken token=candidate instanceof OpenApiToken known?known:null;
    String path=request.getRequestURI()+(request.getQueryString()==null?"":"?"+request.getQueryString());
    logs.record(path,OpenApiManagerController.json(info),new String(result.getContentAsByteArray(),StandardCharsets.UTF_8),token);
   }finally{result.copyBodyToResponse();}
  }
 }
}
