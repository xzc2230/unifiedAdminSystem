package org.jeecg.modules.packagemanagement.domain.pwa;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;

public class AppPwaSub  extends PackageEntity {
    private static final long serialVersionUID = 1L;
    /** id */
    private Long id;


    private String host;

    private String endpoint;

    private PwaKeys keys;
    private String auth;
    private String p256dh;

    private Long appId;

    private Date createTime;

    private Date sendTime;

    private Integer sendStatus;

    private String sendErr;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }


    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    @Override
    public Date getCreateTime() {
        return createTime;
    }

    @Override
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getSendTime() {
        return sendTime;
    }

    public void setSendTime(Date sendTime) {
        this.sendTime = sendTime;
    }

    public Integer getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(Integer sendStatus) {
        this.sendStatus = sendStatus;
    }

    public String getSendErr() {
        return sendErr;
    }

    public void setSendErr(String sendErr) {
        this.sendErr = sendErr;
    }

    public PwaKeys getKeys() {
        return keys;
    }

    public void setKeys(PwaKeys keys) {
        this.keys = keys;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("endpoint", getEndpoint())
                .append("keys", getKeys())
                .toString();
    }
}
