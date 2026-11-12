package org.jeecg.modules.packagemanagement.application.apps.risk.app2;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.time.Clock;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.application.port.GeoPort;
import org.jeecg.modules.packagemanagement.application.port.AppLogPort;
import org.jeecg.modules.packagemanagement.application.legacy.AESUtils;
import org.jeecg.modules.packagemanagement.application.legacy.ClientRiskPayloadCodec;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.application.applog.RequestLogHost;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;

/** Four-segment client protocol, isolated from earlier client implementations. */
@Service
public class App2ClientService {
    @Autowired @Qualifier("packageClock") private Clock clock;
    @Autowired private PackageManagementProperties settings;
    @Autowired private GeoPort geo;
    @Autowired private IAppManagerService appRepository;
    @Autowired private AppLogPort esAppLogRepository;
    @Autowired private App2RiskService app2RiskService;
    @Autowired private App2InboundParser app2InboundParser;
    public Object execute(HttpServletRequest request) {
        AppLog appLog = new AppLog();
        appLog.setReqPath(request.getRequestURI());
        appLog.setReqHost(RequestLogHost.from(request));
        appLog.setReqTime(java.util.Date.from(clock.instant()));
        appLog.setReqMethod(request.getMethod());
        StringBuilder stringBuilder = new StringBuilder();

        String packageName = App2InboundParser.readPkg(request);
        if (StringUtils.isBlank(packageName)) {
            stringBuilder.append("缺少 x-pkg 或请求无效");
            appLog.setErrorMsg(stringBuilder.toString());
            appLog.setResultMsg("");
            log(appLog);
            return null;
        }
        appLog.setPackageName(packageName);

        String ipAddress = geo.getIpAddress(request);
        Map<String, String> cityNameByIp = geo.getFourPathCountryAndCountryCodeByIP(ipAddress);
        appLog.setReqIp("(" + cityNameByIp.get("country") + ")" + ipAddress);

        AppManager app = appRepository.selectAppManagerByPackageName(packageName);
        if (app == null) {
            fillApp2EsReqParams(appLog, request, null);
            stringBuilder.append("包不存在！！！");
            appLog.setErrorMsg(stringBuilder.toString());
            appLog.setResultMsg("");
            log(appLog);
            return null;
        }

        App2RiskService.Evaluation evaluation = app2RiskService.evaluate(request, app, cityNameByIp);
        fillApp2EsReqParams(appLog, request, app);
        if (!evaluation.isPass()) {
            if (StringUtils.isNotBlank(evaluation.getRejectTip())) {
                appLog.setErrorMsg(evaluation.getRejectTip());
            } else {
                appLog.setErrorMsg("风控未通过");
            }
            appLog.setResultMsg("");
            log(appLog);
            return null;
        }

        Map<String, Object> map = new HashMap<>();
        JSONObject jsonObject = null;
        try {
            jsonObject = JSON.parseObject(app.getUrl());
            jsonObject.put("cityCode", cityNameByIp.get("countryCode"));
        } catch (Exception ignored) {
            jsonObject = null;
        }
        map.put("code", app.getStatus() == null ? 0 : app.getStatus());
        map.put("data", jsonObject == null ? app.getUrl() : jsonObject.toJSONString());
        map.put("byme", app.getWhetherUse());
        map.put("ps", app.getMark());
        String jsonString = JSON.toJSONString(map);
        String s = null;
        try {
            String aesKey = StringUtils.isNotBlank(settings.getDefaultAesKey()) ? settings.getDefaultAesKey() : ClientRiskPayloadCodec.defaultXorSecret();
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

    private void fillApp2EsReqParams(AppLog appLog, HttpServletRequest request, AppManager app) {
        try {
            appLog.setReqParams(app2InboundParser.logParamsForEs(request, app).toJSONString());
        } catch (Exception ignored) {
        }
    }

    public void log(AppLog appLog) {
        try {
            if (appLog != null && appLog.getPackageName() != null && StringUtils.isNotBlank(appLog.getPackageName())) {
                esAppLogRepository.save(appLog);
            }

        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(App2ClientService.class).warn("request log was not stored: {}", e.toString());
        }
    }
}
