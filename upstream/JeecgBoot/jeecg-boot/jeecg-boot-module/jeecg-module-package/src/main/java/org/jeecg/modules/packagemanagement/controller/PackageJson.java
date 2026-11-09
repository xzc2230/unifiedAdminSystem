package org.jeecg.modules.packagemanagement.controller;

import com.fasterxml.jackson.databind.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Private compatibility codec; never registered as a host ObjectMapper bean. */
public final class PackageJson {
    private PackageJson() {}
    public static ObjectMapper mapper() {
        var mapper=new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        mapper.setDateFormat(dateFormat());return mapper;
    }
    public static SimpleDateFormat dateFormat() {
        var format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));format.setLenient(false);return format;
    }
}
