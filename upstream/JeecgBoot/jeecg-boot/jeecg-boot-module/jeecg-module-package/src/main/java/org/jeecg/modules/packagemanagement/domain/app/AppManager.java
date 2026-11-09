package org.jeecg.modules.packagemanagement.domain.app;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;

/**
 * 【请填写功能名称】对象 app_manager
 *
 * @author ruoyi
 * @date 2023-10-19
 */
public class AppManager extends PackageEntity {
    private static final long serialVersionUID = 1L;

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * $column.columnComment
     */

    private String appName;

    /**
     * $column.columnComment
     */

    private String areas;

    /**
     * $column.columnComment
     */

    private String author;

    /**
     * $column.columnComment
     */

    private Date createDate;

    /**
     * $column.columnComment
     */

    private String mark;

    /**
     * $column.columnComment
     */

    private String packageCode;

    /**
     * $column.columnComment
     */

    private Integer status;

    /**
     * $column.columnComment
     */

    private Date updateDate;

    /**
     * $column.columnComment
     */

    private String url;

    /**
     * $column.columnComment
     */

    private Integer whetherRestrict;

    /**
     * $column.columnComment
     */

    private Integer whetherUs;

    /**
     * $column.columnComment
     */

    private Integer whetherUse;

    /*
     * 登录用户
     * */
    private Long userId;
    private String userName;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAreas(String areas) {
        this.areas = areas;
    }

    public String getAreas() {
        return areas;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAuthor() {
        return author;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setMark(String mark) {
        this.mark = mark;
    }

    public String getMark() {
        return mark;
    }

    public void setPackageCode(String packageCode) {
        this.packageCode = packageCode;
    }

    public String getPackageCode() {
        return packageCode;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getStatus() {
        return status;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUrl(String url) {
        this.url = url.trim();
    }

    public String getUrl() {
        return url;
    }

    public void setWhetherRestrict(Integer whetherRestrict) {
        this.whetherRestrict = whetherRestrict;
    }

    public Integer getWhetherRestrict() {
        return whetherRestrict;
    }

    public void setWhetherUs(Integer whetherUs) {
        this.whetherUs = whetherUs;
    }

    public Integer getWhetherUs() {
        return whetherUs;
    }

    public void setWhetherUse(Integer whetherUse) {
        this.whetherUse = whetherUse;
    }

    public Integer getWhetherUse() {
        return whetherUse;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("appName", getAppName())
                .append("areas", getAreas())
                .append("author", getAuthor())
                .append("createDate", getCreateDate())
                .append("mark", getMark())
                .append("packageCode", getPackageCode())
                .append("status", getStatus())
                .append("updateDate", getUpdateDate())
                .append("url", getUrl())
                .append("whetherRestrict", getWhetherRestrict())
                .append("whetherUs", getWhetherUs())
                .append("whetherUse", getWhetherUse())
                .append("userId", getUserId())
                .toString();
    }
    private Integer gaidAidThreshold;
    public Integer getGaidAidThreshold() { return gaidAidThreshold; }
    public void setGaidAidThreshold(Integer value) { this.gaidAidThreshold = value; }
    private Integer aidDeviceThreshold;
    public Integer getAidDeviceThreshold() { return aidDeviceThreshold; }
    public void setAidDeviceThreshold(Integer value) { this.aidDeviceThreshold = value; }
    private Integer aidGaidSwitchThreshold;
    public Integer getAidGaidSwitchThreshold() { return aidGaidSwitchThreshold; }
    public void setAidGaidSwitchThreshold(Integer value) { this.aidGaidSwitchThreshold = value; }
    private Integer riskWindowSeconds;
    public Integer getRiskWindowSeconds() { return riskWindowSeconds; }
    public void setRiskWindowSeconds(Integer value) { this.riskWindowSeconds = value; }
    private String referrerBlacklistJson;
    public String getReferrerBlacklistJson() { return referrerBlacklistJson; }
    public void setReferrerBlacklistJson(String value) { this.referrerBlacklistJson = value; }
}
