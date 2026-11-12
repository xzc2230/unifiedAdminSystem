package org.jeecg.modules.packagemanagement.application.applog;

import jakarta.servlet.http.HttpServletRequest;

/** Public hostname from the Host header forwarded by the client gateway. */
public final class RequestLogHost {
    private RequestLogHost() {}

    public static String from(HttpServletRequest request) {
        if (request == null) return "";
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) host = request.getServerName();
        if (host == null) return "";
        host = host.trim();
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            host = end > 1 ? host.substring(1, end) : "";
        } else {
            int colon = host.lastIndexOf(':');
            if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        }
        if (host.length() > 253) host = host.substring(0, 253);
        return host;
    }
}
