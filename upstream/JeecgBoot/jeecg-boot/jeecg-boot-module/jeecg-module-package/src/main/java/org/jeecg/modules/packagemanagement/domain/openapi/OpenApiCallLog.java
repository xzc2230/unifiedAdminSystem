package org.jeecg.modules.packagemanagement.domain.openapi;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * 对外接口调用记录：app_open_api_call_log
 */
public class OpenApiCallLog extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String apiPath;
    private String requestBody;
    private String responseBody;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date callTime;
    private String token;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getApiPath()
    {
        return apiPath;
    }

    public void setApiPath(String apiPath)
    {
        this.apiPath = apiPath;
    }

    public String getRequestBody()
    {
        return requestBody;
    }

    public void setRequestBody(String requestBody)
    {
        this.requestBody = requestBody;
    }

    public String getResponseBody()
    {
        return responseBody;
    }

    public void setResponseBody(String responseBody)
    {
        this.responseBody = responseBody;
    }

    public Date getCallTime()
    {
        return callTime;
    }

    public void setCallTime(Date callTime)
    {
        this.callTime = callTime;
    }

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    private Integer tenantId;
    private String ownerUserId;
    public Integer getTenantId() { return tenantId; }
    public void setTenantId(Integer value) { tenantId=value; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String value) { ownerUserId=value; }
    private Long tokenId;
    public Long getTokenId() { return tokenId; }
    public void setTokenId(Long value) { tokenId=value; }

    private String beginTime;
    private String endTime;
    public String getBeginTime(){return beginTime;}
    public void setBeginTime(String value){beginTime=value;}
    public String getEndTime(){return endTime;}
    public void setEndTime(String value){endTime=value;}
}
