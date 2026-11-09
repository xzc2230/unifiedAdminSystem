package org.jeecg.modules.packagemanagement.application.apps.risk.app2;

import org.jeecg.modules.packagemanagement.domain.app.AppAsnBlacklist;
import org.jeecg.modules.packagemanagement.domain.app.AppCpuAbiBlacklist;
import org.jeecg.modules.packagemanagement.domain.app.AppDeviceBrandBlacklist;
import org.jeecg.modules.packagemanagement.domain.app.AppDeviceNameBlacklist;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppAsnBlacklistMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppCpuAbiBlacklistMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppDeviceBrandBlacklistMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppDeviceNameBlacklistMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 全局风控（对所有包生效，优先于单包规则）。
 * 包含：ASN/品牌/ABI/设备型号黑名单（时区/SIM 白名单暂不使用）。
 * IP 地区规则在 App2RiskService.checkGeo 中处理。
 */
@Service
public class App2GlobalRiskEvaluator {

    // 时区/SIM 白名单暂不使用
    // private static final String TIP_TZ_MISSING = "已开启系统时区白名单但缺少时区字段";
    // private static final String TIP_SIM_MISSING = "已开启 SIM 国家白名单但缺少 f1";
    // private static final String TIP_TZ_NOT_ALLOWED = "客户端时区不在系统时区白名单内";
    // private static final String TIP_SIM_NOT_ALLOWED = "SIM 国家不在 SIM 国家白名单内";
    private static final String TIP_ASN_BLOCK = "命中 ASN 黑名单";
    private static final String TIP_BRAND_BLOCK = "命中设备品牌黑名单";
    private static final String TIP_ABI_BLOCK = "命中 CPU ABI 黑名单";
    private static final String TIP_DEVICE_NAME_BLOCK = "命中设备型号黑名单";

    @Autowired private AppAsnBlacklistMapper appAsnBlacklistMapper;
    @Autowired private AppCpuAbiBlacklistMapper appCpuAbiBlacklistMapper;
    @Autowired private AppDeviceBrandBlacklistMapper appDeviceBrandBlacklistMapper;
    @Autowired private AppDeviceNameBlacklistMapper appDeviceNameBlacklistMapper;

    public App2RiskResult evaluate(AppManager app, App2DevicePayload device, Map<String, String> cityMap) {
        // 时区/SIM 白名单暂不使用
        // boolean tzOn = Objects.equals(app.getWhetherTimezoneRestrict(), 1) && StringUtils.isNotBlank(app.getSystemTimezone());
        // boolean simOn = Objects.equals(app.getWhetherSimCountryRestrict(), 1) && StringUtils.isNotBlank(app.getSimCountry());
        boolean bl = anyEnabledBlacklistRow();
        if (!bl) { return App2RiskResult.ok(); }
        // if (tzOn && StringUtils.isBlank(device.getTimezone())) { return App2RiskResult.reject(TIP_TZ_MISSING); }
        // if (simOn && StringUtils.isBlank(device.getSimCountry())) { return App2RiskResult.reject(TIP_SIM_MISSING); }
        // if (tzOn && !timezoneAllowed(app, device.getTimezone())) { return App2RiskResult.reject(TIP_TZ_NOT_ALLOWED); }
        // if (simOn && !simAllowed(app, device.getSimCountry())) { return App2RiskResult.reject(TIP_SIM_NOT_ALLOWED); }
        App2RiskResult asn = checkAsn(cityMap); if (!asn.isPass()) { return asn; }
        App2RiskResult brand = checkBrand(device.brandAbiHaystack()); if (!brand.isPass()) { return brand; }
        App2RiskResult abi = checkAbi(device.getCpuAbi(), device.brandAbiHaystack()); if (!abi.isPass()) { return abi; }
        return checkDeviceName(device.getDeviceName());
    }

    // 时区/SIM 白名单暂不使用
    // private boolean timezoneAllowed(AppManager app, String clientTz) {
    //     for (String piece : app.getSystemTimezone().split(",")) {
    //         if (clientTz.trim().equals(piece.trim())) { return true; }
    //     }
    //     return false;
    // }
    //
    // private boolean simAllowed(AppManager app, String f1) {
    //     String v = f1.trim().toLowerCase();
    //     for (String piece : app.getSimCountry().split(",")) {
    //         if (v.equals(StringUtils.trimToEmpty(piece).toLowerCase())) { return true; }
    //     }
    //     return false;
    // }

    private App2RiskResult checkAsn(Map<String, String> cityMap) {
        AppAsnBlacklist q = new AppAsnBlacklist(); q.setEnabled(1);
        String asnReq = App2KeywordMatcher.normalizeAsnDigits(cityMap.get("asn"));
        for (AppAsnBlacklist row : appAsnBlacklistMapper.selectAppAsnBlacklistList(q)) {
            if (App2KeywordMatcher.isObserveAction(row.getAction())) { continue; }
            String asnRule = App2KeywordMatcher.normalizeAsnDigits(row.getAsn());
            if (StringUtils.isNotBlank(asnRule) && asnRule.equals(asnReq)) { return App2RiskResult.reject(TIP_ASN_BLOCK); }
        }
        return App2RiskResult.ok();
    }

    private App2RiskResult checkBrand(String haystack) {
        AppDeviceBrandBlacklist q = new AppDeviceBrandBlacklist(); q.setEnabled(1);
        for (AppDeviceBrandBlacklist row : appDeviceBrandBlacklistMapper.selectAppDeviceBrandBlacklistList(q)) {
            if (App2KeywordMatcher.isObserveAction(row.getAction())) { continue; }
            if (App2KeywordMatcher.matches(haystack, row.getBrandKeyword(), row.getMatchMode(), row.getCaseSensitive())) {
                return App2RiskResult.reject(TIP_BRAND_BLOCK);
            }
        }
        return App2RiskResult.ok();
    }

    private App2RiskResult checkAbi(String cpuAbi, String haystack) {
        String abiHay = StringUtils.isNotBlank(cpuAbi) ? cpuAbi : haystack;
        AppCpuAbiBlacklist q = new AppCpuAbiBlacklist(); q.setEnabled(1);
        for (AppCpuAbiBlacklist row : appCpuAbiBlacklistMapper.selectAppCpuAbiBlacklistList(q)) {
            if (App2KeywordMatcher.isObserveAction(row.getAction())) { continue; }
            if (App2KeywordMatcher.matches(abiHay, row.getAbiKeyword(), row.getMatchMode(), row.getCaseSensitive())) {
                return App2RiskResult.reject(TIP_ABI_BLOCK);
            }
        }
        return App2RiskResult.ok();
    }

    private App2RiskResult checkDeviceName(String deviceName) {
        if (StringUtils.isBlank(deviceName)) { return App2RiskResult.ok(); }
        AppDeviceNameBlacklist q = new AppDeviceNameBlacklist(); q.setEnabled(1);
        for (AppDeviceNameBlacklist row : appDeviceNameBlacklistMapper.selectAppDeviceNameBlacklistList(q)) {
            if (App2KeywordMatcher.isObserveAction(row.getAction())) { continue; }
            if (App2KeywordMatcher.matches(deviceName, row.getDeviceNameKeyword(), row.getMatchMode(), row.getCaseSensitive())) {
                return App2RiskResult.reject(TIP_DEVICE_NAME_BLOCK);
            }
        }
        return App2RiskResult.ok();
    }

    private boolean anyEnabledBlacklistRow() {
        AppAsnBlacklist q1 = new AppAsnBlacklist(); q1.setEnabled(1);
        if (!appAsnBlacklistMapper.selectAppAsnBlacklistList(q1).isEmpty()) { return true; }
        AppDeviceBrandBlacklist q2 = new AppDeviceBrandBlacklist(); q2.setEnabled(1);
        if (!appDeviceBrandBlacklistMapper.selectAppDeviceBrandBlacklistList(q2).isEmpty()) { return true; }
        AppCpuAbiBlacklist q3 = new AppCpuAbiBlacklist(); q3.setEnabled(1);
        if (!appCpuAbiBlacklistMapper.selectAppCpuAbiBlacklistList(q3).isEmpty()) { return true; }
        AppDeviceNameBlacklist q4 = new AppDeviceNameBlacklist(); q4.setEnabled(1);
        return !appDeviceNameBlacklistMapper.selectAppDeviceNameBlacklistList(q4).isEmpty();
    }
}
