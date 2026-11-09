package org.jeecg.modules.packagemanagement.domain.notify;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * 消息推送对象 app_notify
 *
 * @author ruoyi
 * @date 2024-09-27
 */
public class AppNotify extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    /** id */
    private Long id;

    private Long appId;

    /** app名称 */

    private String appName;

    /** ap图标 */

    private String appIcon;

    /** ap域名 */

    private String appHost;

    /** 通知标题 */

    private String notifyTitle;
    /** 用户ID */

    private Long userId;
    /** 通知内容 */

    private String notifyMessage;

    /** 通知图标 */

    private String notifyIcon;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
    public void setAppHost(String appHost)
    {
        this.appHost = appHost;
    }

    public String getAppHost()
    {
        return appHost;
    }
    public void setNotifyTitle(String notifyTitle)
    {
        this.notifyTitle = notifyTitle;
    }

    public String getNotifyTitle()
    {
        return notifyTitle;
    }
    public void setNotifyMessage(String notifyMessage)
    {
        this.notifyMessage = notifyMessage;
    }

    public String getNotifyMessage()
    {
        return notifyMessage;
    }
    public void setNotifyIcon(String notifyIcon)
    {
        this.notifyIcon = notifyIcon;
    }

    public String getNotifyIcon()
    {
        return notifyIcon;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("appName", getAppName())
            .append("appIcon", getAppIcon())
            .append("appHost", getAppHost())
            .append("notifyTitle", getNotifyTitle())
            .append("notifyMessage", getNotifyMessage())
            .append("notifyIcon", getNotifyIcon())
            .append("createTime", getCreateTime())
            .toString();
    }
}
