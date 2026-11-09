package org.jeecg.modules.packagemanagement.controller.legacy;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/** Only explicitly configured immediate peers can supply forwarding metadata. */
@Component @Order(-105)
public class PackageProxyFilter extends OncePerRequestFilter {
    private final Set<String> trusted;
    private static final Set<String> FORWARDED=Set.of("x-forwarded-for","proxy-client-ip","wl-proxy-client-ip","http_client_ip","http_x_forwarded_for","forwarded","x-forwarded-host","x-forwarded-proto","x-original-uri");
    public PackageProxyFilter(@Value("${jeecg.package-management.trusted-proxies:}") String peers) {
        trusted=new HashSet<>(Arrays.stream(peers.split(",")).map(String::trim).filter(v->!v.isEmpty()).toList());
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().startsWith(request.getContextPath()+"/package-legacy/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        boolean allowed=trusted.contains(request.getRemoteAddr());
        if(allowed)request.setAttribute("package.trustedOriginalUri",request.getHeader("X-Original-URI"));
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public String getScheme(){String scheme=allowed?super.getHeader("X-Forwarded-Proto"):null;return "https".equals(scheme)||"http".equals(scheme)?scheme:super.getScheme();}
            @Override public boolean isSecure(){return "https".equals(getScheme());}
            @Override public int getServerPort(){return allowed&&"https".equals(super.getHeader("X-Forwarded-Proto"))&&super.getServerPort()==80?443:super.getServerPort();}
            private boolean hidden(String name){return !allowed&&FORWARDED.contains(name.toLowerCase(Locale.ROOT));}
            @Override public String getHeader(String name){return hidden(name)?null:super.getHeader(name);}
            @Override public Enumeration<String> getHeaders(String name){return hidden(name)?Collections.emptyEnumeration():super.getHeaders(name);}
            @Override public Enumeration<String> getHeaderNames(){return Collections.enumeration(Collections.list(super.getHeaderNames()).stream().filter(n->!hidden(n)).toList());}
        },response);
    }
}
