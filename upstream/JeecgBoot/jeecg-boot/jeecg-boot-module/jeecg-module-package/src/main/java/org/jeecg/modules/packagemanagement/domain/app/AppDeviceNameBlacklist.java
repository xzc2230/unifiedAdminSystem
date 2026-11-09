package org.jeecg.modules.packagemanagement.domain.app;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AppDeviceNameBlacklist extends PackageEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String deviceNameKeyword;
    private String matchMode;
    private Integer caseSensitive;
    private String action;
    private Integer enabled;
    private Integer priority;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceNameKeyword() {
        return deviceNameKeyword;
    }

    public void setDeviceNameKeyword(String deviceNameKeyword) {
        this.deviceNameKeyword = deviceNameKeyword;
    }

    public String getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(String matchMode) {
        this.matchMode = matchMode;
    }

    public Integer getCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(Integer caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("deviceNameKeyword", getDeviceNameKeyword())
                .append("matchMode", getMatchMode())
                .append("caseSensitive", getCaseSensitive())
                .append("action", getAction())
                .append("enabled", getEnabled())
                .append("priority", getPriority())
                .append("remark", getRemark())
                .toString();
    }
}
