package org.jeecg.modules.packagemanagement.domain.app;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * ASN blacklist row for app_asn_blacklist.
 */
public class AppAsnBlacklist extends PackageEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String asn;
    private String asnName;
    private String orgKeyword;
    private String matchType;
    private String action;
    private Integer enabled;
    private Integer priority;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAsn() {
        return asn;
    }

    public void setAsn(String asn) {
        this.asn = asn;
    }

    public String getAsnName() {
        return asnName;
    }

    public void setAsnName(String asnName) {
        this.asnName = asnName;
    }

    public String getOrgKeyword() {
        return orgKeyword;
    }

    public void setOrgKeyword(String orgKeyword) {
        this.orgKeyword = orgKeyword;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
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
                .append("asn", getAsn())
                .append("asnName", getAsnName())
                .append("orgKeyword", getOrgKeyword())
                .append("matchType", getMatchType())
                .append("action", getAction())
                .append("enabled", getEnabled())
                .append("priority", getPriority())
                .append("remark", getRemark())
                .toString();
    }
}
