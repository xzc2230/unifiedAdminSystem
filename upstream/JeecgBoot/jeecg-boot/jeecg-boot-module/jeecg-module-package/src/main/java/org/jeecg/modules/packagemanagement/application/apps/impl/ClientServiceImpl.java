package org.jeecg.modules.packagemanagement.application.apps.impl;


import cn.hutool.crypto.digest.DigestUtil;
import org.jeecg.modules.packagemanagement.application.apps.support.PackageCountryRegions;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.application.apps.ClientService;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.application.applog.RequestLogHost;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;
import org.jeecg.modules.packagemanagement.application.port.AppLogPort;
import org.jeecg.modules.packagemanagement.application.legacy.AESUtils;
import org.jeecg.modules.packagemanagement.application.port.GeoPort;
import org.jeecg.modules.packagemanagement.application.legacy.ParamsUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ClientServiceImpl implements ClientService {

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("packageClock")
    private java.time.Clock clock;
    @Autowired
    private org.jeecg.modules.packagemanagement.config.PackageManagementProperties settings;
    @Autowired
    private GeoPort geo;

    @Autowired
    private IAppManagerService appRepository;
    @Autowired
    AppLogPort esAppLogRepository;

    @Autowired private org.jeecg.modules.packagemanagement.application.apps.risk.app2.App2ClientService app2ClientService;
    @Override public Object app2(HttpServletRequest request) { return app2ClientService.execute(request); }

    @Override
    public Object api(HttpServletRequest request) {
        String ipAddress = geo.getIpAddress(request);
        String cityByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(ipAddress));
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqIp("(" + cityByIp + ")" + ipAddress);
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("status", 0);
        String packageName = request.getParameter("id");
        String afId = request.getParameter("af_id");

        if (StringUtils.isBlank(packageName)) {
            try {
                appLog.setResultMsg(JSONObject.toJSONString(resultMap));
                stringBuilder.append("必传参数为空！！");
                appLog.setErrorMsg(stringBuilder.toString());
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);

            return resultMap;
        }

        try {
            packageName = new String(Base64.getDecoder().decode(packageName));

        } catch (Exception e) {
            try {
                appLog.setResultMsg(JSONObject.toJSONString(resultMap));
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg("base64 解密失败！！");
            } catch (Exception e1) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);

            return resultMap;
        }

        appLog.setPackageName(packageName);

        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null || app.getStatus() == 0) {
            appLog.setErrorMsg("包不存在！！");
            try {
                appLog.setResultMsg(JSONObject.toJSONString(resultMap));
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return resultMap;
        }

        if (app.getWhetherUs() == 1) {
            if ("us".equals(PackageCountryRegions.code(cityByIp))) {
                try {
                    appLog.setResultMsg("禁用美国及google、apple、amazon等运营商：" + JSONObject.toJSONString(resultMap));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);

                return resultMap;
            }
        }
        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, cityByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(resultMap));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return resultMap;
                }
            } else {
                if (!PackageCountryRegions.matches(areas, cityByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(resultMap));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return resultMap;
                }
            }
        }


        String url = app.getUrl();
        StringBuilder builder = new StringBuilder(url);
        if (!StringUtils.isBlank(afId) && app.getWhetherUse().equals(1)) {
            try {
                String query = new URL(url).getQuery();
                if (query != null && !query.isEmpty()) {
                    builder.append("&appsflyer_id=").append(afId);
                } else {
                    builder.append("?appsflyer_id=").append(afId);
                }
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }

        }
        resultMap.put("status", app.getStatus());
        resultMap.put("url", Base64.getEncoder().encodeToString(builder.toString().getBytes(Charset.defaultCharset())));
        resultMap.put("code", app.getWhetherUse());
        try {
            appLog.setResultMsg(JSONObject.toJSONString(resultMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
            appLog.setErrorMsg(stringBuilder.toString());
        }
        log(appLog);
        return resultMap;
    }


    @Override
    public Object login(HttpServletRequest request) {
        String ipAddress = geo.getIpAddress(request);
        String cityByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(ipAddress));
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqIp("(" + cityByIp + ")" + ipAddress);
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        Map<String, Object> map = new HashMap<>();
        map.put("code", -1);
        map.put("message", "user does not exist");
        map.put("data", new HashMap<>());
        String packageName = request.getParameter("username");
        String afID = request.getParameter("password");
        if (StringUtils.isBlank(packageName)) {
            try {
                appLog.setResultMsg(JSONObject.toJSONString(map));
                stringBuilder.append("必传参数为空！！");
                appLog.setErrorMsg(stringBuilder.toString());
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return map;
        }
        appLog.setPackageName(packageName);
        AppManager app = appRepository.selectAppManagerByPackageName(packageName);

        if (app == null || app.getStatus() == 0) {

            try {
                appLog.setResultMsg(JSONObject.toJSONString(map));
                stringBuilder.append("包不存在！！");
                appLog.setErrorMsg(stringBuilder.toString());
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return map;
        }
        if (app.getWhetherUs() == 1) {
            if ("us".equals(PackageCountryRegions.code(cityByIp))) {
                try {
                    appLog.setResultMsg("禁用美国及google、apple、amazon等运营商：" + JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return map;
            }
        }
        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, cityByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(map));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return map;
                }
            } else {
                if (!PackageCountryRegions.matches(areas, cityByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(map));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return map;
                }
            }
        }


        map.put("code", 1);
        map.put("message", "request was successful");
        String url = app.getUrl();
        StringBuilder builder = new StringBuilder(url);
        if (!StringUtils.isBlank(afID) && app.getWhetherUse().equals(1)) {
            try {
                String query = new URL(url).getQuery();
                if (query != null && !query.isEmpty()) {
                    builder.append("&appsflyer_id=").append(afID);
                } else {
                    builder.append("?appsflyer_id=").append(afID);
                }
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
        }

        HashMap<String, Object> stringObjectHashMap = new HashMap<>();
        stringObjectHashMap.put("show", app.getStatus());
        stringObjectHashMap.put("path", builder.toString());
        stringObjectHashMap.put("useAF", app.getWhetherUse());
        map.put("data", stringObjectHashMap);
        try {
            appLog.setResultMsg(JSONObject.toJSONString(map));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
            appLog.setErrorMsg(stringBuilder.toString());
        }
        log(appLog);

        return map;
    }

    @Override
    public void redirected(HttpServletRequest request, HttpServletResponse response) {
        String ipAddress = geo.getIpAddress(request);
        String cityByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(ipAddress));
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqIp("(" + cityByIp + ")" + ipAddress);
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }
        try {
            String packageName = request.getParameter("packName");
            String afID = request.getParameter("af_id");
            if (StringUtils.isBlank(packageName)) {
                response.sendRedirect("about:blank");
                try {
                    appLog.setResultMsg("重定向接口：about:blank");
                    stringBuilder.append("必传参数为空！！");
                    appLog.setErrorMsg(stringBuilder.toString());
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);

                return;
            }
            appLog.setPackageName(packageName);

            AppManager app = appRepository.selectAppManagerByPackageName(packageName);

            if (app == null) {
                response.sendRedirect("about:blank");
                try {
                    appLog.setResultMsg("重定向接口：about:blank");
                    stringBuilder.append("包不存在！！");
                    appLog.setErrorMsg(stringBuilder.toString());
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return;
            }


            if (app.getWhetherUs() == 1) {
                if ("us".equals(PackageCountryRegions.code(cityByIp))) {
                    String mark = app.getMark();
                    if (StringUtils.isBlank(mark)) {
                        response.sendRedirect("about:blank");
                    } else {
                        response.sendRedirect(mark);
                    }

                    try {
                        appLog.setResultMsg("禁用美国及google、apple、amazon等运营商：" + (StringUtils.isBlank(mark) ? "about:blank" : mark));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);

                    return;
                }
            }
            if (app.getWhetherRestrict() == 1) {
                String areas = Objects.toString(app.getAreas(), "");
                if (areas.contains(",")) {
                    String[] split = areas.split(",");
                    List<String> list = Arrays.asList(split);
                    if (!PackageCountryRegions.matches(areas, cityByIp)) {
                        String mark = app.getMark();
                        if (StringUtils.isBlank(mark)) {
                            response.sendRedirect("about:blank");
                            stringBuilder.append("不在白名单");
                            appLog.setErrorMsg(stringBuilder.toString());
                            try {
                                appLog.setResultMsg("about:blank");
                            } catch (Exception e) {
                                stringBuilder.append(e.getMessage());
                                appLog.setErrorMsg(stringBuilder.toString());
                            }
                            log(appLog);
                        } else {
                            response.sendRedirect(mark);
                            stringBuilder.append("不在白名单");
                            appLog.setErrorMsg(stringBuilder.toString());
                            try {
                                appLog.setResultMsg("about:blank");
                            } catch (Exception e) {
                                stringBuilder.append(e.getMessage());
                                appLog.setErrorMsg(stringBuilder.toString());
                            }
                            log(appLog);
                        }
                        return;
                    }
                } else {
                    if (!PackageCountryRegions.matches(areas, cityByIp)) {
                        String mark = app.getMark();
                        if (StringUtils.isBlank(mark)) {
                            response.sendRedirect("about:blank");
                            stringBuilder.append("不在白名单");
                            appLog.setErrorMsg(stringBuilder.toString());
                            try {
                                appLog.setResultMsg("about:blank");
                            } catch (Exception e) {
                                stringBuilder.append(e.getMessage());
                                appLog.setErrorMsg(stringBuilder.toString());
                            }
                            log(appLog);
                        } else {
                            response.sendRedirect(mark);
                            stringBuilder.append("不在白名单");
                            appLog.setErrorMsg(stringBuilder.toString());
                            try {
                                appLog.setResultMsg("about:blank");
                            } catch (Exception e) {
                                stringBuilder.append(e.getMessage());
                                appLog.setErrorMsg(stringBuilder.toString());
                            }
                            log(appLog);
                        }
                        return;
                    }
                }
            }


            String url = app.getUrl();
            StringBuilder builder = new StringBuilder(url);
            if (!StringUtils.isBlank(afID) && app.getWhetherUse().equals(1)) {
                String query = new URL(url).getQuery();
                if (query != null && !query.isEmpty()) {
                    builder.append("&appsflyer_id=").append(afID);
                } else {
                    builder.append("?appsflyer_id=").append(afID);
                }
            }

            if (app.getStatus() == 0) {
                String mark = app.getMark();
                if (StringUtils.isBlank(mark)) {
                    response.sendRedirect("about:blank");
                } else {
                    response.sendRedirect(mark);
                }

                try {
                    appLog.setResultMsg(StringUtils.isBlank(mark) ? "about:blank" : mark);
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);

            } else {
                try {
                    appLog.setResultMsg("重定向：" + builder);
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);

                response.sendRedirect(builder.toString());
            }
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
            appLog.setErrorMsg(stringBuilder.toString());
            log(appLog);
        }

    }

    @Override
    public Object getBanner(HttpServletRequest request) {
        String ipAddress = geo.getIpAddress(request);
        String cityByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(ipAddress));
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqIp("(" + cityByIp + ")" + ipAddress);
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        String packageName = request.getParameter("systemName");
        String afID = request.getParameter("systemID");

        if (StringUtils.isBlank(packageName)) {
            try {
                appLog.setResultMsg("");
                stringBuilder.append("必传参数为空！！");
                appLog.setErrorMsg(stringBuilder.toString());
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }

            return "";
        }

        appLog.setPackageName(packageName);
        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null) {

            try {
                appLog.setResultMsg("");
                stringBuilder.append("包不存在！！");
                appLog.setErrorMsg(stringBuilder.toString());
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);

            return "";
        }

        if (app.getWhetherUs() == 1) {
            if ("us".equals(PackageCountryRegions.code(cityByIp))) {
                String mark = app.getMark();
                if (StringUtils.isBlank(mark)) {
                    try {
                        appLog.setResultMsg("禁用美国及google、apple、amazon等运营商：");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);

                    return "";
                } else {
                    try {
                        appLog.setResultMsg("禁用美国及google、apple、amazon等运营商：备注：" + mark);
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);

                    return mark;
                }
            }
        }
        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, cityByIp)) {
                    String mark = app.getMark();
                    if (StringUtils.isBlank(mark)) {
                        stringBuilder.append("不在白名单");
                        appLog.setErrorMsg(stringBuilder.toString());
                        try {
                            appLog.setResultMsg("");
                        } catch (Exception e) {
                            stringBuilder.append(e.getMessage());
                            appLog.setErrorMsg(stringBuilder.toString());
                        }
                        log(appLog);

                        return "";
                    } else {
                        stringBuilder.append("不在白名单");
                        appLog.setErrorMsg(stringBuilder.toString());
                        try {
                            appLog.setResultMsg("备注：" + mark);
                        } catch (Exception e) {
                            stringBuilder.append(e.getMessage());
                            appLog.setErrorMsg(stringBuilder.toString());
                        }
                        log(appLog);

                        return mark;
                    }
                }
            } else {
                if (!PackageCountryRegions.matches(areas, cityByIp)) {
                    String mark = app.getMark();
                    if (StringUtils.isBlank(mark)) {
                        stringBuilder.append("不在白名单");
                        appLog.setErrorMsg(stringBuilder.toString());
                        try {
                            appLog.setResultMsg("");
                        } catch (Exception e) {
                            stringBuilder.append(e.getMessage());
                            appLog.setErrorMsg(stringBuilder.toString());
                        }
                        log(appLog);

                        return "";
                    } else {
                        stringBuilder.append("不在白名单");
                        appLog.setErrorMsg(stringBuilder.toString());
                        try {
                            appLog.setResultMsg("备注：" + mark);
                        } catch (Exception e) {
                            stringBuilder.append(e.getMessage());
                            appLog.setErrorMsg(stringBuilder.toString());
                        }
                        log(appLog);
                        return mark;
                    }
                }
            }
        }


        try {
            String url = app.getUrl();
            StringBuilder builder = new StringBuilder(url);
            if (!StringUtils.isBlank(afID) && app.getWhetherUse().equals(1)) {
                try {
                    String query = new URL(url).getQuery();
                    if (query != null && !query.isEmpty()) {
                        builder.append("&appsflyer_id=").append(afID);
                    } else {
                        builder.append("?appsflyer_id=").append(afID);
                    }

                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
            }

            if (app.getStatus() == 0) {
                String mark = app.getMark();
                if (StringUtils.isBlank(mark)) {
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);


                    return "";
                } else {
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return mark;
                }
            } else {
                String string = builder.toString();
                try {
                    appLog.setResultMsg(string);
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return string;
            }
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
            appLog.setErrorMsg(stringBuilder.toString());
            log(appLog);
            return "";
        }
    }

    @Override
    public Object checkUrl(HttpServletRequest request) {
        String ipAddress = geo.getIpAddress(request);
        String cityNameByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(ipAddress));
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqIp("(" + cityNameByIp + ")" + ipAddress);
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        String header = request.getHeader("tokenID");
        String baseTime = request.getHeader("baseTime");
        String packageName = request.getParameter(header);
        String afID = request.getParameter(baseTime);

        Map<String, Object> map = new HashMap<>();
        map.put("event", false);
        map.put("canLoad", false);
        map.put("canAppend", false);
        map.put("load", "");
        appLog.setPackageName(packageName);
        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null) {

            try {
                stringBuilder.append("包不存在！！");
                appLog.setErrorMsg(stringBuilder.toString());
                appLog.setResultMsg(JSONObject.toJSONString(map));
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);

            return map;
        }


        if (app.getWhetherUs() == 1) {
            if ("us".equals(PackageCountryRegions.code(cityNameByIp))) {
                try {
                    appLog.setResultMsg("禁用美国及google、apple、amazon等运营商：" + JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);


                return map;
            }
        }

        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(map));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return map;
                }
            } else {
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(map));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return map;
                }
            }
        }


        try {
            String url = app.getUrl();
            StringBuilder builder = new StringBuilder(url);
            if (!StringUtils.isBlank(afID) && app.getWhetherUse() == 1) {
                try {
                    String query = new URL(url).getQuery();
                    if (query != null && !query.isEmpty()) {
                        builder.append("&appsflyer_id=").append(afID);
                    } else {
                        builder.append("?appsflyer_id=").append(afID);
                    }

                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
            }

            if (app.getStatus() == 0) {
                try {
                    appLog.setResultMsg(JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return map;
            } else {
                map.put("event", true);
                map.put("canLoad", app.getStatus() == 1);
                map.put("canAppend", app.getWhetherUse() == 1);
                map.put("load", builder.toString());
                map.put("token", afID);
                try {
                    appLog.setResultMsg(JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return map;
            }
        } catch (Exception ignored) {
            try {
                appLog.setResultMsg(JSONObject.toJSONString(map));
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return map;
        }
    }

    @Override
    public Object dynamicAdj(HttpServletRequest request) {
        String ipAddress = geo.getIpAddress(request);
        String cityNameByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(ipAddress));
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqIp("(" + cityNameByIp + ")" + ipAddress);
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        String packageName = request.getParameter("id");
        Map<String, Object> map = new HashMap<>();
        map.put("app_id", "");
        map.put("app_cache", 0);
        map.put("app_limit", "");
        map.put("app_name", "");
        map.put("app_dynamic", "");
        appLog.setPackageName(packageName);

        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null) {


            try {
                stringBuilder.append("包不存在！！");
                appLog.setErrorMsg(stringBuilder.toString());
                appLog.setResultMsg(JSONObject.toJSONString(map));
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);

            return map;
        }


        if (app.getWhetherUs() == 1) {
            if ("us".equals(PackageCountryRegions.code(cityNameByIp))) {
                try {
                    appLog.setResultMsg("禁用美国及google、apple、amazon等运营商：" + JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);

                return map;
            }
        }

        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(map));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return map;
                }
            } else {
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(map));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return map;
                }
            }
        }

        try {
            if (app.getStatus() == 0) {
                try {
                    appLog.setResultMsg(JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);

                return map;
            } else {
                map.put("app_id", app.getPackageCode());
                map.put("app_cache", app.getStatus());
                map.put("app_limit", app.getUrl());
                map.put("app_name", app.getAppName());
                map.put("app_dynamic", app.getMark());
                try {
                    appLog.setResultMsg(JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);

                return map;
            }
        } catch (Exception ignored) {
            try {
                appLog.setResultMsg(JSONObject.toJSONString(map));
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return map;
        }
    }

    @Override
    public Object getUserInfo(HttpServletRequest request) {
        String ipAddress = geo.getIpAddress(request);
        String cityNameByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(ipAddress));
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqIp("(" + cityNameByIp + ")" + ipAddress);
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        Map<String, Object> map = new HashMap<>();
        map.put("timestamp", clock.millis() - 65535L);
        map.put("token", DigestUtil.md5Hex(String.valueOf(clock.millis())));
        map.put("ty", request.getHeader("User-Agent"));
        map.put("v", "1");
        map.put("imgs", "");
        map.put("isApp", 0);
        map.put("user", "");
        map.put("user_info", "");
        String packageName = request.getParameter("user_id");
        String afID = request.getParameter("user_token");
        appLog.setPackageName(packageName);
        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null) {
            try {
                stringBuilder.append("包不存在！！");
                appLog.setErrorMsg(stringBuilder.toString());
                appLog.setResultMsg(JSONObject.toJSONString(map));
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return map;
        }
        if (app.getWhetherUs() == 1) {
            if ("us".equals(PackageCountryRegions.code(cityNameByIp))) {
                try {
                    appLog.setResultMsg("禁用美国及google、apple、amazon等运营商：" + JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return map;
            }
        }
        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(map));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return map;
                }
            } else {
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg(JSONObject.toJSONString(map));
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return map;
                }
            }
        }

        try {
            String url = app.getUrl();
            StringBuilder builder = new StringBuilder(url);
            if (!StringUtils.isBlank(afID) && app.getWhetherUse() == 1) {
                String query = new URL(url).getQuery();
                if (query != null && !query.isEmpty()) {
                    builder.append("&appsflyer_id=").append(afID);
                } else {
                    builder.append("?appsflyer_id=").append(afID);
                }
            }


            if (app.getStatus() == 0) {
                map.put("imgs", app.getAppName());
                try {
                    appLog.setResultMsg(JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return map;
            } else {
                map.put("imgs", builder.toString());
                map.put("isApp", app.getStatus());
                map.put("user", PackageCountryRegions.normalizeAreas(app.getAreas()));
                map.put("user_info", app.getMark());
                try {
                    appLog.setResultMsg(JSONObject.toJSONString(map));
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);

                return map;
            }
        } catch (Exception ignored) {
            try {
                appLog.setResultMsg(JSONObject.toJSONString(map));
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return map;
        }

    }

    @Override
    public Object base64Data(HttpServletRequest request) {
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        String packageName = request.getParameter("pack_a");
        String jsonData = request.getParameter("data_a");
        appLog.setPackageName(packageName);

        String ipA = "";
        try {
            JSONObject jsonObject = JSON.parseObject(jsonData);
            if (jsonObject != null) {
                if (jsonObject.containsKey("ip_a")) {
                    ipA = jsonObject.getString("ip_a");
                }
                if (jsonObject.containsKey("sim_a")) {
                    String simA = jsonObject.getString("sim_a");
                    if (!simA.contains("5")) {
                        try {
                            appLog.setResultMsg("");
                        } catch (Exception e) {
                            stringBuilder.append(e.getMessage());
                            appLog.setErrorMsg(stringBuilder.toString());
                        }
                        log(appLog);
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
            appLog.setErrorMsg(stringBuilder.toString());
        }
        String ipAddress = geo.getIpAddress(request);
        String cityNameByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(ipA.trim().isEmpty() ? ipAddress : ipA));
        appLog.setReqIp("(" + cityNameByIp + ")" + ipAddress);

        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null || app.getStatus() == 0) {
            try {
                stringBuilder.append("包不存在或开关没开！！");
                appLog.setErrorMsg(stringBuilder.toString());
                appLog.setResultMsg("");
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);

            return null;
        }


        if (app.getWhetherUs() == 1) {
            if ("us".equals(PackageCountryRegions.code(cityNameByIp))) {
                try {
                    appLog.setResultMsg("禁用美国及google、apple、amazon等运营商：");
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return null;
            }
        }

        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return null;
                }
            } else {
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return null;
                }
            }
        }
        String mark = app.getMark();
        String url = app.getUrl();
        if (mark != null && !mark.trim().isEmpty()) {
            if (!cityNameByIp.isEmpty()) {
                try {
                    JSONObject markObject = JSON.parseObject(mark);
                    url = PackageCountryRegions.regionalLink(markObject, cityNameByIp, url);
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }

            }
        }


        Map<String, Object> map = new HashMap<>();
        map.put("s_coe", app.getStatus());
        map.put("link_oth", url);
        map.put("by_me", app.getWhetherUse());
        String jsonString = JSON.toJSONString(map);
        String s = Base64.getEncoder().encodeToString(jsonString.getBytes(StandardCharsets.UTF_8));
        String packString = packageName.replaceAll("\\.", "");

        String s1 = s + packString;

        try {
            appLog.setResultMsg(s1);
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
            appLog.setErrorMsg(stringBuilder.toString());
        }
        log(appLog);

        return s1;
    }

    @Override
    public Object base64Data2(HttpServletRequest request) {
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        String packageName = request.getParameter("index");
        appLog.setPackageName(packageName);
        String ipAddress = geo.getIpAddress(request);
        String cityNameByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(ipAddress));
        appLog.setReqIp("(" + cityNameByIp + ")" + ipAddress);
        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null || app.getStatus() == 0) {
            try {
                stringBuilder.append("包不存在或开关没开！！！");
                appLog.setErrorMsg(stringBuilder.toString());
                appLog.setResultMsg("");
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return null;
        }
        if (app.getWhetherUs() == 1) {
            if ("us".equals(PackageCountryRegions.code(cityNameByIp))) {
                try {
                    appLog.setResultMsg("禁用美国及google、apple、amazon等运营商");
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return null;
            }
        }
        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return null;
                }
            } else {
                if (!PackageCountryRegions.matches(areas, cityNameByIp)) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return null;
                }
            }
        }

        String mark = app.getMark();
        String url = app.getUrl();
        if (mark != null && !mark.trim().isEmpty()) {
            if (!cityNameByIp.isEmpty()) {
                try {
                    JSONObject markObject = JSON.parseObject(mark);
                    url = PackageCountryRegions.regionalLink(markObject, cityNameByIp, url);
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }

            }
        }
        Map<String, Object> map = new HashMap<>();
        map.put("total_pages", app.getStatus());
        map.put("list_details", url);
        map.put("max_page", app.getWhetherUse());
        map.put("data_type", "JSON");
        map.put("group", Base64.getEncoder().encodeToString(String.valueOf(clock.millis()).getBytes()));
        map.put("isNow", clock.millis());
        map.put("date_time", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(clock.getZone()).format(clock.instant()));
        String jsonString = JSON.toJSONString(map);

        String s = Base64.getEncoder().encodeToString(jsonString.getBytes(StandardCharsets.UTF_8));
        String packString = packageName.replaceAll("\\.", "");
        String s1 = s + packString;
        try {
            appLog.setResultMsg(s1);
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
            appLog.setErrorMsg(stringBuilder.toString());
        }
        log(appLog);
        return s1;
    }

    @Override
    public Object base64Data3(HttpServletRequest request) {
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Map<String, String[]> parameterMap = request.getParameterMap();
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        String packageName = request.getParameter("timestamp");
        appLog.setPackageName(packageName);
        String ipAddress = geo.getIpAddress(request);
        Map<String, String> cityNameByIp = geo.getCountryAndCountryCodeByIP(ipAddress);
        appLog.setReqIp("(" + cityNameByIp.get("country") + ")" + ipAddress);
        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null || app.getStatus() == 0) {
            try {
                stringBuilder.append("包不存在或开关没开！！！");
                appLog.setErrorMsg(stringBuilder.toString());
                appLog.setResultMsg("");
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return null;
        }
        if (app.getWhetherUs() == 1) {
            if (cityNameByIp.get("status").equals("true") || "us".equals(PackageCountryRegions.geoCode(cityNameByIp))) {
                try {
                    appLog.setResultMsg("禁用美国及google、apple、amazon等运营商");
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return null;
            }
        }
        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, PackageCountryRegions.geoCode(cityNameByIp))) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return null;
                }
            } else {
                if (!PackageCountryRegions.matches(areas, PackageCountryRegions.geoCode(cityNameByIp))) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return null;
                }
            }
        }

        Map<String, Object> map = new HashMap<>();
        JSONObject jsonObject = null;
        try {
            jsonObject = JSON.parseObject(app.getUrl());
            jsonObject.put("cityCode", cityNameByIp.get("countryCode"));
        } catch (Exception ignored) {
            jsonObject = null;
        }
        map.put("pid", app.getStatus());
        map.put("origin", jsonObject == null ? app.getUrl() : jsonObject.toJSONString());
        map.put("spid", app.getWhetherUse());
        map.put("mark", app.getMark());
        String jsonString = JSON.toJSONString(map);
        String s = Base64.getEncoder().encodeToString(jsonString.getBytes(StandardCharsets.UTF_8));
        try {
            appLog.setResultMsg(s);
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
            appLog.setErrorMsg(stringBuilder.toString());
        }
        log(appLog);
        return s;
    }

    @Override
    public Object app1(HttpServletRequest request) {
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap.isEmpty()) {
            stringBuilder.append("包不存在或开关没开！！！");
            appLog.setErrorMsg(stringBuilder.toString());
            appLog.setResultMsg("");
            log(appLog);
            return null;
        }
        try {
            appLog.setReqParams(JSON.toJSONString(parameterMap));
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        String packageName = ParamsUtils.getParams(parameterMap);
        appLog.setPackageName(packageName);
        String ipAddress = geo.getIpAddress(request);
        Map<String, String> cityNameByIp = geo.getCountryAndCountryCodeByIP(ipAddress);
        appLog.setReqIp("(" + cityNameByIp.get("country") + ")" + ipAddress);
        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null || app.getStatus() == 0) {
            try {
                stringBuilder.append("包不存在或开关没开！！！");
                appLog.setErrorMsg(stringBuilder.toString());
                appLog.setResultMsg("");
            } catch (Exception e) {
                stringBuilder.append(e.getMessage());
                appLog.setErrorMsg(stringBuilder.toString());
            }
            log(appLog);
            return null;
        }
        if (app.getWhetherUs() == 1) {
            if (cityNameByIp.get("status").equals("true") || "us".equals(PackageCountryRegions.geoCode(cityNameByIp))) {
                try {
                    appLog.setResultMsg("禁用美国及google、apple、amazon等运营商");
                } catch (Exception e) {
                    stringBuilder.append(e.getMessage());
                    appLog.setErrorMsg(stringBuilder.toString());
                }
                log(appLog);
                return null;
            }
        }
        if (app.getWhetherRestrict() == 1) {
            String areas = Objects.toString(app.getAreas(), "");
            if (areas.contains(",")) {
                String[] split = areas.split(",");
                List<String> list = Arrays.asList(split);
                if (!PackageCountryRegions.matches(areas, PackageCountryRegions.geoCode(cityNameByIp))) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return null;
                }
            } else {
                if (!PackageCountryRegions.matches(areas, PackageCountryRegions.geoCode(cityNameByIp))) {
                    stringBuilder.append("不在白名单");
                    appLog.setErrorMsg(stringBuilder.toString());
                    try {
                        appLog.setResultMsg("");
                    } catch (Exception e) {
                        stringBuilder.append(e.getMessage());
                        appLog.setErrorMsg(stringBuilder.toString());
                    }
                    log(appLog);
                    return null;
                }
            }
        }

        Map<String, Object> map = new HashMap<>();
        JSONObject jsonObject = null;
        try {
            jsonObject = JSON.parseObject(app.getUrl());
            jsonObject.put("cityCode", cityNameByIp.get("countryCode"));
        } catch (Exception ignored) {
            jsonObject = null;
        }
        map.put("code", app.getStatus());
        map.put("data", jsonObject == null ? app.getUrl() : jsonObject.toJSONString());
        map.put("byme", app.getWhetherUse());
        map.put("ps", app.getMark());
        String jsonString = JSON.toJSONString(map);
        String s = null;

        try {
            String aesKey = settings.getDefaultAesKey();
            if (app.getAppName() != null && StringUtils.isNotBlank(app.getAppName())) {
                aesKey = app.getAppName();
            }

            s = AESUtils.encrypt(jsonString, aesKey);
            appLog.setResultMsg(s);
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
            appLog.setErrorMsg(stringBuilder.toString());
        }
        log(appLog);
        return s;
    }


    public void log(AppLog appLog) {
        try {
            if (appLog != null && appLog.getPackageName() != null && StringUtils.isNotBlank(appLog.getPackageName())) {
                esAppLogRepository.save(appLog);
            }

        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ClientServiceImpl.class).warn("request log was not stored: {}", e.toString());
        }
    }
}
