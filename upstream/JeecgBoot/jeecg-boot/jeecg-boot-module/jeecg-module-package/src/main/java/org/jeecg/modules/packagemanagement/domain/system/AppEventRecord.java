package org.jeecg.modules.packagemanagement.domain.system;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * 事件统计对象 app_event_record
 *
 * @author ruoyi
 * @date 2024-10-11
 */
public class AppEventRecord extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键id */
    private Long eventId;

    /** $column.columnComment */

    private Long appId;

    /** 域名 */

    private String hostName;

    /** 事件类型 */

    private Long eventType;

    /** 事件名称 */

    private String eventTypeName;

    /** 设备编号 */

    private String deviceNo;

    /** IP */

    private String ip;

    /** $column.columnComment */

    private Long userId;

    public void setEventId(Long eventId)
    {
        this.eventId = eventId;
    }

    public Long getEventId()
    {
        return eventId;
    }
    public void setAppId(Long appId)
    {
        this.appId = appId;
    }

    public Long getAppId()
    {
        return appId;
    }
    public void setHostName(String hostName)
    {
        this.hostName = hostName;
    }

    public String getHostName()
    {
        return hostName;
    }
    public void setEventType(Long eventType)
    {
        this.eventType = eventType;
    }

    public Long getEventType()
    {
        return eventType;
    }
    public void setEventTypeName(String eventTypeName)
    {
        this.eventTypeName = eventTypeName;
    }

    public String getEventTypeName()
    {
        return eventTypeName;
    }
    public void setDeviceNo(String deviceNo)
    {
        this.deviceNo = deviceNo;
    }

    public String getDeviceNo()
    {
        return deviceNo;
    }
    public void setIp(String ip)
    {
        this.ip = ip;
    }

    public String getIp()
    {
        return ip;
    }
    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("eventId", getEventId())
            .append("appId", getAppId())
            .append("hostName", getHostName())
            .append("eventType", getEventType())
            .append("eventTypeName", getEventTypeName())
            .append("deviceNo", getDeviceNo())
            .append("ip", getIp())
            .append("createTime", getCreateTime())
            .append("userId", getUserId())
            .toString();
    }
}
