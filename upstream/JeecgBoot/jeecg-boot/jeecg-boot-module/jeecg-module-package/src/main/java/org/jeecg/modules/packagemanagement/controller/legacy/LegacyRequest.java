package org.jeecg.modules.packagemanagement.controller.legacy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** Presents the source-facing route to migrated business code. */
public final class LegacyRequest extends HttpServletRequestWrapper {
    private static final String PREFIX = "/package-legacy";

    private LegacyRequest(HttpServletRequest request) { super(request); }

    public static HttpServletRequest wrap(HttpServletRequest request) { return new LegacyRequest(request); }

    @Override public String getRequestURI() {
        Object original=getAttribute("package.trustedOriginalUri");
        if(original instanceof String value&&value.startsWith("/")&&!value.startsWith("//")&&!value.contains("\r")&&!value.contains("\n"))return value.split("\\?",2)[0];
        String uri = super.getRequestURI();
        String prefix = super.getContextPath() + PREFIX;
        return uri.startsWith(prefix + "/") || uri.equals(prefix)
                ? uri.substring(prefix.length()) : uri;
    }

    @Override public String getServletPath() {
        String path = super.getServletPath();
        return path.startsWith(PREFIX + "/") || path.equals(PREFIX)
                ? path.substring(PREFIX.length()) : path;
    }

    @Override public String getContextPath() { return ""; }
}
