package org.jeecg.modules.packagemanagement.domain.event;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * af事件记录对象 evnet_history
 *
 * @author ruoyi
 * @date 2024-04-16
 */
public class EvnetHistory extends PackageEntity {
    private static final long serialVersionUID = 1L;

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * $column.columnComment
     */

    private String eventName;

    /**
     * $column.columnComment
     */

    private String eventTime;

    /**
     * $column.columnComment
     */

    private String eventSource;

    /**
     * $column.columnComment
     */

    private String mediaSource;

    /**
     * $column.columnComment
     */

    private String campaign;

    /**
     * $column.columnComment
     */

    private String afSub1;

    /**
     * $column.columnComment
     */

    private String afSub2;

    /**
     * $column.columnComment
     */

    private String afSub3;

    /**
     * $column.columnComment
     */

    private String region;

    /**
     * $column.columnComment
     */

    private String countryCode;

    /**
     * $column.columnComment
     */

    private String state;

    /**
     * $column.columnComment
     */

    private String city;

    /**
     * $column.columnComment
     */

    private String postalCode;

    /**
     * $column.columnComment
     */

    private String dma;

    /**
     * $column.columnComment
     */

    private String ip;

    /**
     * $column.columnComment
     */

    private String wifi;

    /**
     * $column.columnComment
     */

    private String operator;

    /**
     * $column.columnComment
     */

    private String language;

    /**
     * $column.columnComment
     */

    private String appsflyerId;

    /**
     * $column.columnComment
     */

    private String platform;

    /**
     * $column.columnComment
     */

    private String osVersion;

    /**
     * $column.columnComment
     */

    private String appVersion;

    /**
     * $column.columnComment
     */

    private String sdkVersion;

    /**
     * $column.columnComment
     */

    private String appId;

    /**
     * $column.columnComment
     */

    private String appName;

    /**
     * $column.columnComment
     */

    private String bundleId;

    /**
     * $column.columnComment
     */

    private String isRetargeting;

    /**
     * $column.columnComment
     */

    private String userAgent;

    /**
     * $column.columnComment
     */

    private String httpReferrer;

    /**
     * $column.columnComment
     */

    private String originalUrl;

    /**
     * $column.columnComment
     */

    private String deviceDownloadTime;

    /**
     * $column.columnComment
     */

    private String conversionType;

    /**
     * $column.columnComment
     */

    private String deviceModel;

    /**
     * $column.columnComment
     */

    private String jsonStr;

    /**
     * $column.columnComment
     */

    private String path;

    /**
     * 同步到fb的状态，0 失败 1 成功
     */

    private String status;

    /**
     * $column.columnComment
     */

    private String packageName;

    /* 币种*/

    private String selected_currency;
    /*金额*/
    private Float event_revenue_usd;

    public String getSelected_currency() {
        return selected_currency;
    }

    public void setSelected_currency(String selected_currency) {
        this.selected_currency = selected_currency;
    }

    public Float getEvent_revenue_usd() {
        return event_revenue_usd;
    }

    public void setEvent_revenue_usd(Float event_revenue_usd) {
        this.event_revenue_usd = event_revenue_usd;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }

    public String getEventSource() {
        return eventSource;
    }

    public void setMediaSource(String mediaSource) {
        this.mediaSource = mediaSource;
    }

    public String getMediaSource() {
        return mediaSource;
    }

    public void setCampaign(String campaign) {
        this.campaign = campaign;
    }

    public String getCampaign() {
        return campaign;
    }

    public void setAfSub1(String afSub1) {
        this.afSub1 = afSub1;
    }

    public String getAfSub1() {
        return afSub1;
    }

    public void setAfSub2(String afSub2) {
        this.afSub2 = afSub2;
    }

    public String getAfSub2() {
        return afSub2;
    }

    public void setAfSub3(String afSub3) {
        this.afSub3 = afSub3;
    }

    public String getAfSub3() {
        return afSub3;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getRegion() {
        return region;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCity() {
        return city;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setDma(String dma) {
        this.dma = dma;
    }

    public String getDma() {
        return dma;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    public void setWifi(String wifi) {
        this.wifi = wifi;
    }

    public String getWifi() {
        return wifi;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getOperator() {
        return operator;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLanguage() {
        return language;
    }

    public void setAppsflyerId(String appsflyerId) {
        this.appsflyerId = appsflyerId;
    }

    public String getAppsflyerId() {
        return appsflyerId;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getPlatform() {
        return platform;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setSdkVersion(String sdkVersion) {
        this.sdkVersion = sdkVersion;
    }

    public String getSdkVersion() {
        return sdkVersion;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppName() {
        return appName;
    }

    public void setBundleId(String bundleId) {
        this.bundleId = bundleId;
    }

    public String getBundleId() {
        return bundleId;
    }

    public void setIsRetargeting(String isRetargeting) {
        this.isRetargeting = isRetargeting;
    }

    public String getIsRetargeting() {
        return isRetargeting;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setHttpReferrer(String httpReferrer) {
        this.httpReferrer = httpReferrer;
    }

    public String getHttpReferrer() {
        return httpReferrer;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setDeviceDownloadTime(String deviceDownloadTime) {
        this.deviceDownloadTime = deviceDownloadTime;
    }

    public String getDeviceDownloadTime() {
        return deviceDownloadTime;
    }

    public void setConversionType(String conversionType) {
        this.conversionType = conversionType;
    }

    public String getConversionType() {
        return conversionType;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setJsonStr(String jsonStr) {
        this.jsonStr = jsonStr;
    }

    public String getJsonStr() {
        return jsonStr;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("eventName", getEventName())
                .append("eventTime", getEventTime())
                .append("eventSource", getEventSource())
                .append("mediaSource", getMediaSource())
                .append("campaign", getCampaign())
                .append("afSub1", getAfSub1())
                .append("afSub2", getAfSub2())
                .append("afSub3", getAfSub3())
                .append("region", getRegion())
                .append("countryCode", getCountryCode())
                .append("state", getState())
                .append("city", getCity())
                .append("postalCode", getPostalCode())
                .append("dma", getDma())
                .append("ip", getIp())
                .append("wifi", getWifi())
                .append("operator", getOperator())
                .append("language", getLanguage())
                .append("appsflyerId", getAppsflyerId())
                .append("platform", getPlatform())
                .append("osVersion", getOsVersion())
                .append("appVersion", getAppVersion())
                .append("sdkVersion", getSdkVersion())
                .append("appId", getAppId())
                .append("appName", getAppName())
                .append("bundleId", getBundleId())
                .append("isRetargeting", getIsRetargeting())
                .append("userAgent", getUserAgent())
                .append("httpReferrer", getHttpReferrer())
                .append("originalUrl", getOriginalUrl())
                .append("deviceDownloadTime", getDeviceDownloadTime())
                .append("conversionType", getConversionType())
                .append("deviceModel", getDeviceModel())
                .append("jsonStr", getJsonStr())
                .append("path", getPath())
                .append("status", getStatus())
                .append("packageName", getPackageName())
                .toString();
    }
}
