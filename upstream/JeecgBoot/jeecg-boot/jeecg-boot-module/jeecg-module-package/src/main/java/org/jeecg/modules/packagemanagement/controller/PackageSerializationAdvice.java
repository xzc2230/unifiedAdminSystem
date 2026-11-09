package org.jeecg.modules.packagemanagement.controller;

import java.util.Date;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages="org.jeecg.modules.packagemanagement.controller")
public class PackageSerializationAdvice implements ResponseBodyAdvice<Object> {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper=PackageJson.mapper();
    @InitBinder public void dates(WebDataBinder binder) {
        binder.registerCustomEditor(Date.class,new org.springframework.beans.propertyeditors.CustomDateEditor(PackageJson.dateFormat(),true));
    }
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) {
        return !org.springframework.http.converter.StringHttpMessageConverter.class.isAssignableFrom(converter);
    }
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(body==null||body instanceof String||body instanceof byte[])return body;
        return mapper.convertValue(body,Object.class);
    }
}
