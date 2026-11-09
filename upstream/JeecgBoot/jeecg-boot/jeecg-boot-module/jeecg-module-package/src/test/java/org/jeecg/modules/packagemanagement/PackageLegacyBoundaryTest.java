package org.jeecg.modules.packagemanagement;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.controller.legacy.*;
import org.springframework.mock.web.*;
import org.springframework.data.redis.core.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageLegacyBoundaryTest {
    @Test void repeatUsesOriginalUriTrimmedTokenAndRawBodyOrParameterMap() {
        var redis=mock(StringRedisTemplate.class);ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);Map<String,String> state=new HashMap<>();
        when(values.get(anyString())).thenAnswer(a->state.get(a.getArgument(0)));
        doAnswer(a->{state.put(a.getArgument(0),a.getArgument(1));return null;}).when(values).set(anyString(),anyString(),anyLong(),any());
        var clock=mock(Clock.class);when(clock.millis()).thenReturn(1000L);
        var repeat=new PackageRepeatSubmit(redis,clock);var request=new MockHttpServletRequest("POST","/jeecg-boot/package-legacy/systemSDK/a");
        request.setContextPath("/jeecg-boot");request.addHeader("Authorization"," token ");request.addParameter("x","1");
        assertFalse(repeat.repeated(request,null));assertTrue(repeat.repeated(request,""));
        assertTrue(state.containsKey("pkg:repeat_submit:/systemSDK/atoken"));
        assertFalse(repeat.repeated(request,"{\"x\":1}"));assertTrue(repeat.repeated(request,"{\"x\":1}"));
        when(clock.millis()).thenReturn(2000L);assertFalse(repeat.repeated(request,"{\"x\":1}"));
    }
    @Test void onlyTrustedImmediatePeerCanSupplyOriginalUriAndIp() throws Exception {
        var filter=new PackageProxyFilter("127.0.0.1");
        var request=new MockHttpServletRequest("GET","/jeecg-boot/package-legacy/system/a");request.setContextPath("/jeecg-boot");
        request.setRemoteAddr("192.0.2.1");request.addHeader("X-Forwarded-For","198.51.100.1");request.addHeader("X-Original-URI","/external?a=1");
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->{var http=(jakarta.servlet.http.HttpServletRequest)req;assertNull(http.getHeader("X-Forwarded-For"));assertEquals("/system/a",LegacyRequest.wrap(http).getRequestURI());});
        request.setRemoteAddr("127.0.0.1");
        filter.doFilter(request,response,(req,res)->{var http=(jakarta.servlet.http.HttpServletRequest)req;assertEquals("198.51.100.1",http.getHeader("X-Forwarded-For"));assertEquals("/external",LegacyRequest.wrap(http).getRequestURI());});
    }
}
