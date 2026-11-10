package org.jeecg.modules.packagemanagement.domain.openapi;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * 对外接口 Token：app_open_api_token
 */
public class OpenApiToken extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String token;
    /** 空=永久 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;
    /** 1启用 0停用 */
    private Integer status;
    private String remark;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    public Date getExpireTime()
    {
        return expireTime;
    }

    public void setExpireTime(Date expireTime)
    {
        this.expireTime = expireTime;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    @Override
    public String getRemark()
    {
        return remark;
    }

    @Override
    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    private Integer tenantId;
    private String ownerUserId;
    public Integer getTenantId() { return tenantId; }
    public void setTenantId(Integer value) { tenantId=value; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String value) { ownerUserId=value; }
}
