package org.jeecg.modules.packagemanagement.domain.pwa;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;
import java.util.List;

/**
 * pwa对象 app_pwa
 *
 * @author Murphy
 * @date 2024-08-09
 */
public class AppPwa extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    /** id */
    private Long id;

    /** 应用名 */

    private String appName;

    /** 应用icon */

    private String appIcon;

    /** 应用五图 */

    private String appAdvertising;

    /** 应用描述 */

    private String appDescription;

    /** 应用语言 */

    private String appLanguage;

    /** 域名 */

    private String appHost;

    /** 域名 */

    private String appSubHost;

    /** 域名 */

    private String appSub;

    /** 包网链接 */

    private String appUrl;

    /** 应用事件 */

    private String appEvent;

    /** 事件配置 */

    private String appEventConf;

    /** 推广链接ID */

    private String linkId;


    /** 用户ID */
    private Long userId;

    /** 创建时间 */
    private Date appCreateTime;

    /** 修改时间 */
    private Date appChangeTime;

    private String publicKey;

    private String privateKey;
    private String downloadUrl;
    private String appType;
    private String appCode;
    private Integer downloadCount;
    private String score;
    private Integer reviews;
    private String tags;

    private List<ReviewsRecord> reviewsRecords;


    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }
    public void setAppName(String appName)
    {
        this.appName = appName;
    }

    public String getAppName()
    {
        return appName;
    }
    public void setAppIcon(String appIcon)
    {
        this.appIcon = appIcon;
    }

    public String getAppIcon()
    {
        return appIcon;
    }
    public void setAppAdvertising(String appAdvertising)
    {
        this.appAdvertising = appAdvertising;
    }

    public String getAppAdvertising()
    {
        return appAdvertising;
    }
    public void setAppDescription(String appDescription)
    {
        this.appDescription = appDescription;
    }

    public String getAppDescription()
    {
        return appDescription;
    }
    public void setAppLanguage(String appLanguage)
    {
        this.appLanguage = appLanguage;
    }

    public String getAppLanguage()
    {
        return appLanguage;
    }
    public void setAppHost(String appHost)
    {
        this.appHost = appHost;
    }

    public String getAppHost()
    {
        return appHost;
    }

    public void setAppSubHost(String appSubHost) {
        this.appSubHost = appSubHost;
    }

    public String getAppSubHost() {
        return appSubHost;
    }

    public String getAppSub() {
        return appSub;
    }

    public void setAppSub(String appSub) {
        this.appSub = appSub;
    }

    public void setAppUrl(String appUrl)
    {
        this.appUrl = appUrl;
    }

    public String getAppUrl()
    {
        return appUrl;
    }
    public void setAppEvent(String appEvent)
    {
        this.appEvent = appEvent;
    }

    public String getAppEvent()
    {
        return appEvent;
    }
    public void setAppEventConf(String appEventConf)
    {
        this.appEventConf = appEventConf;
    }

    public String getAppEventConf()
    {
        return appEventConf;
    }
    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }
    public void setAppCreateTime(Date appCreateTime)
    {
        this.appCreateTime = appCreateTime;
    }

    public Date getAppCreateTime()
    {
        return appCreateTime;
    }
    public void setAppChangeTime(Date appChangeTime)
    {
        this.appChangeTime = appChangeTime;
    }

    public Date getAppChangeTime()
    {
        return appChangeTime;
    }

    public String getLinkId() {
        return linkId;
    }

    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getAppType() {
        return appType;
    }

    public void setAppType(String appType) {
        this.appType = appType;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
    }

    public String getScore() {
        return score;
    }

    public void setScore(String score) {
        this.score = score;
    }

    public Integer getReviews() {
        return reviews;
    }

    public void setReviews(Integer reviews) {
        this.reviews = reviews;
    }

    public List<ReviewsRecord> getReviewsRecords() {
        return reviewsRecords;
    }

    public void setReviewsRecords(List<ReviewsRecord> reviewsRecords) {
        this.reviewsRecords = reviewsRecords;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("appName", getAppName())
            .append("appIcon", getAppIcon())
            .append("appAdvertising", getAppAdvertising())
            .append("appDescription", getAppDescription())
            .append("appLanguage", getAppLanguage())
            .append("appHost", getAppHost())
            .append("appSubHost", getAppSubHost())
            .append("appSub", getAppSub())
            .append("appUrl", getAppUrl())
            .append("appEvent", getAppEvent())
            .append("appEventConf", getAppEventConf())
            .append("userId", getUserId())
            .append("appCreateTime", getAppCreateTime())
            .append("appChangeTime", getAppChangeTime())
            .append("linkId", getLinkId())
            .append("publicKey", getPublicKey())
            .append("privateKey", getPrivateKey())
            .append("downloadUrl", getDownloadUrl())
            .append("appType", getAppType())
            .append("appCode", getAppCode())
            .append("downloadCount", getDownloadUrl())
            .append("score", getScore())
            .append("reviews", getReviews())
            .toString();
    }
}
