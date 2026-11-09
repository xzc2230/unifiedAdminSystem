package org.jeecg.modules.packagemanagement.application.apps.risk.app2;

import com.alibaba.fastjson2.JSONObject;

/**
 * XOR 解密后的设备环境 JSON 载荷。
 * <p>
 * 除 V4 字段（brand、cpuAbi、deviceName、aid、gaid、InstallReferrer）外，
 * 仍保留时区（tz/timezone/t）与 SIM（f1）以兼容历史全局白名单风控。
 */
public final class App2DevicePayload {

    private final String brand;
    private final String cpuAbi;
    private final String deviceName;
    private final String aid;
    private final String gaid;
    /** 系统时区（IANA），对应历史 UA 载荷字段 t / tz */
    private final String timezone;
    /** SIM 国家二位码，对应历史 UA 载荷字段 f1 */
    private final String simCountry;
    /** Android Install Referrer API 组装对象 */
    private final JSONObject installReferrer;

    public App2DevicePayload(String brand, String cpuAbi, String deviceName, String aid, String gaid,
                             String timezone, String simCountry, JSONObject installReferrer) {
        this.brand = brand;
        this.cpuAbi = cpuAbi;
        this.deviceName = deviceName;
        this.aid = aid;
        this.gaid = gaid;
        this.timezone = timezone;
        this.simCountry = simCountry;
        this.installReferrer = installReferrer;
    }

    public String getBrand() { return brand; }
    public String getCpuAbi() { return cpuAbi; }
    public String getDeviceName() { return deviceName; }
    public String getAid() { return aid; }
    public String getGaid() { return gaid; }
    public String getTimezone() { return timezone; }
    public String getSimCountry() { return simCountry; }
    public JSONObject getInstallReferrer() { return installReferrer; }

    /** 单包风控设备指纹：brand + deviceName */
    public String deviceFingerprint() {
        String b = brand == null ? "" : brand.trim();
        String d = deviceName == null ? "" : deviceName.trim();
        if (b.isEmpty() && d.isEmpty()) { return ""; }
        return (b + " " + d).trim();
    }

    /** 全局品牌/ABI 黑名单匹配串 */
    public String brandAbiHaystack() {
        String b = brand == null ? "" : brand.trim();
        String a = cpuAbi == null ? "" : cpuAbi.trim();
        return (b + " " + a).trim();
    }
}
