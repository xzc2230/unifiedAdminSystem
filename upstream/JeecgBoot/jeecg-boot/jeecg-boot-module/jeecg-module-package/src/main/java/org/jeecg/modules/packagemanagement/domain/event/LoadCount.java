package org.jeecg.modules.packagemanagement.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;

/**
 * 落地页访问量对象 load_count
 *
 * @author ruoyi
 * @date 2024-05-07
 */
public class LoadCount extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    /** id */
    private Long id;

    /** 域名 */

    private String hostName;

    /** 时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")

    private Date time;

    /** 访问量 */

    private Long access;

    /** 下载量 */

    private Long download;
    private Long userId;
    private String appName;
    private Long appId;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }
    public void setHostName(String hostName)
    {
        this.hostName = hostName;
    }

    public String getHostName()
    {
        return hostName;
    }
    public void setTime(Date time)
    {
        this.time = time;
    }

    public Date getTime()
    {
        return time;
    }
    public void setAccess(Long access)
    {
        this.access = access;
    }

    public Long getAccess()
    {
        return access;
    }
    public void setDownload(Long download)
    {
        this.download = download;
    }

    public Long getDownload()
    {
        return download;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("hostName", getHostName())
            .append("time", getTime())
            .append("access", getAccess())
            .append("download", getDownload())
            .toString();
    }
}
