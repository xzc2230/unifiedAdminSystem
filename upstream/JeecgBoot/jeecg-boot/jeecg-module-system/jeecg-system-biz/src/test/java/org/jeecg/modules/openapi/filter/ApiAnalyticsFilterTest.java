package org.jeecg.modules.openapi.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.jeecg.modules.analytics.AnalyticsService;
import org.jeecg.modules.openapi.entity.*;
import org.jeecg.modules.openapi.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.WebApplicationContext;
import static org.mockito.Mockito.*;

class ApiAnalyticsFilterTest {
    @Test void storageOutageDoesNotChangeBusinessStatusAndMetadataHasNoSecrets() throws Exception {
        var analytics = mock(AnalyticsService.class);
        doThrow(new IllegalStateException("storage down")).when(analytics).record(any(), any(), anyInt(), anyLong());
        var application = mock(WebApplicationContext.class);
        var log = mock(OpenApiLogService.class);
        var auth = mock(OpenApiAuthService.class);
        when(application.getBean(OpenApiLogService.class)).thenReturn(log);
        when(application.getBean(OpenApiAuthService.class)).thenReturn(auth);
        when(application.getBean(OpenApiService.class)).thenReturn(mock(OpenApiService.class));
        when(application.getBean(OpenApiPermissionService.class)).thenReturn(mock(OpenApiPermissionService.class));
        when(application.getBean(AnalyticsService.class)).thenReturn(analytics);
        var context = mock(ServletContext.class);
        when(context.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE)).thenReturn(application);
        var config = mock(FilterConfig.class); when(config.getServletContext()).thenReturn(context);
        var request = mock(HttpServletRequest.class); var response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET"); when(request.getRequestURI()).thenReturn("/openapi/call/test");
        when(request.getHeader("appkey")).thenReturn("private-ak");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getStatus()).thenReturn(201);
        var api = new OpenApi(); api.setId("api-1");
        var filter = spy(new ApiAuthFilter()); filter.init(config);
        doReturn(api).when(filter).findOpenApi(request);
        doNothing().when(filter).checkWhiteList(any(), any());
        doNothing().when(filter).checkSignValid(any(), any(), any());
        doNothing().when(filter).checkSignature(any(), any(), any(), any());
        doNothing().when(filter).checkPermission(any(), any());
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verify(analytics).record(eq("api-1"), eq("GET"), eq(201), anyLong());
        verify(response, never()).setStatus(anyInt());
        verify(log).save(any());
    }
}
