package org.jeecg.modules.packagemanagement.application.apps.risk.app2;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.application.legacy.ClientRiskPayloadCodec;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * V4 入参解析：Header {@link App2RiskConstants#HEADER_PKG} + Body {@code {t,p}}。
 * <p>
 * {@code p} 使用现有 {@link ClientRiskPayloadCodec}（Base64(XOR) 明文 JSON），
 * 密钥优先取 {@link AppManager#getAppName()}，否则默认密钥。
 */
@Service
public class App2InboundParser {
    @Autowired private PackageManagementProperties settings;
    private static final String BODY_ATTRIBUTE = App2InboundParser.class.getName() + ".body";
    private static String body(HttpServletRequest request) {
        Object cached = request.getAttribute(BODY_ATTRIBUTE);
        if (cached instanceof String) return (String) cached;
        try {
            String value = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            request.setAttribute(BODY_ATTRIBUTE, value);
            return value;
        } catch (IOException e) { return ""; }
    }


    /** 解析成功的入参快照 */
    public static final class Parsed {
        /** 包名（x-pkg） */
        private final String pkg;
        /** 客户端秒级时间戳（body.t，仅记录不参与 XOR 解密） */
        private final String clientTimestamp;
        /** 解密后的设备环境 */
        private final App2DevicePayload payload;

        Parsed(String pkg, String clientTimestamp, App2DevicePayload payload) {
            this.pkg = pkg;
            this.clientTimestamp = clientTimestamp;
            this.payload = payload;
        }

        public String getPkg() { return pkg; }
        public String getClientTimestamp() { return clientTimestamp; }
        public App2DevicePayload getPayload() { return payload; }
    }

    /** 仅读取包名，用于先查 app_manager 再完整解析 */
    public static String readPkg(HttpServletRequest request) {
        return readPkgHeader(request);
    }

    /**
     * 完整解析请求；任一环节失败返回 null。
     * @param app 可为 null（默认密钥）；查到包后应传入以使用包密钥
     */
    public Parsed parse(HttpServletRequest request, AppManager app) {
        String pkg = readPkgHeader(request);
        if (StringUtils.isBlank(pkg)) { return null; }
        String body = body(request);
        if (StringUtils.isBlank(body)) { return null; }
        JSONObject root;
        try { root = JSON.parseObject(body); } catch (Exception e) { return null; }
        if (root == null) { return null; }
        String t = root.getString("t");
        String p = root.getString("p");
        if (StringUtils.isBlank(p)) { return null; }
        JSONObject device = ClientRiskPayloadCodec.tryDecodeJson(p, xorSecret(app));
        if (device == null) { return null; }
        return new Parsed(pkg.trim(), t, mapDevice(device));
    }

    /** 写 ES 日志：x-pkg、t、解密后的 p（不存密文 p）、UA */
    public JSONObject logParamsForEs(HttpServletRequest request, AppManager app) {
        JSONObject o = new JSONObject();
        o.put("x-pkg", readPkgHeader(request));
        String body = body(request);
        if (StringUtils.isNotBlank(body)) {
            try {
                JSONObject root = JSON.parseObject(body);
                if (root != null) {
                    if (root.containsKey("t")) {
                        o.put("t", root.get("t"));
                    }
                    String pCipher = root.getString("p");
                    if (StringUtils.isNotBlank(pCipher)) {
                        JSONObject decoded = ClientRiskPayloadCodec.tryDecodeJson(pCipher, xorSecret(app));
                        if (decoded != null) {
                            o.put("p", decoded);
                        }
                    }
                } else {
                    o.put("body", body);
                }
            } catch (Exception ignored) {
                o.put("body", body);
            }
        }
        String ua = request.getHeader("User-Agent");
        o.put("User-Agent", ua != null ? ua : "");
        return o;
    }

    private static String readPkgHeader(HttpServletRequest request) {
        String pkg = request.getHeader(App2RiskConstants.HEADER_PKG);
        if (StringUtils.isBlank(pkg)) { pkg = request.getHeader("X-Pkg"); }
        return pkg;
    }

    private String xorSecret(AppManager app) {
        if (app != null && StringUtils.isNotBlank(app.getAppName())) { return app.getAppName(); }
        return settings != null && StringUtils.isNotBlank(settings.getDefaultAesKey())
            ? settings.getDefaultAesKey() : ClientRiskPayloadCodec.defaultXorSecret();
    }

    /** 解密 JSON 映射为载荷，兼容旧字段名 */
    private static App2DevicePayload mapDevice(JSONObject jo) {
        String brand = firstNonBlank(jo.getString("brand"), jo.getString("b"));
        String cpuAbi = firstNonBlank(jo.getString("cpuAbi"), jo.getString("abi"));
        String deviceName = StringUtils.trimToEmpty(jo.getString("deviceName"));
        String aid = StringUtils.trimToEmpty(jo.getString("aid"));
        String gaid = StringUtils.trimToEmpty(jo.getString("gaid"));
        String tz = firstNonBlank(jo.getString("tz"), jo.getString("timezone"), jo.getString("t"));
        String f1 = StringUtils.trimToEmpty(jo.getString("f1"));
        JSONObject installReferrer = jo.getJSONObject("InstallReferrer");
        if (installReferrer == null && jo.get("InstallReferrer") instanceof JSONObject) {
            installReferrer = (JSONObject) jo.get("InstallReferrer");
        }
        return new App2DevicePayload(brand, cpuAbi, deviceName, aid, gaid, tz, f1, installReferrer);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) { return ""; }
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) { return v.trim(); }
        }
        return "";
    }
}
