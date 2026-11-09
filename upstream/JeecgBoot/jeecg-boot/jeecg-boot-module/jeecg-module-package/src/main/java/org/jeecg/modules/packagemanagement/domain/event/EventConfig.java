package org.jeecg.modules.packagemanagement.domain.event;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * fb_config对象 event_config
 *
 * @author ruoyi
 * @date 2024-04-16
 */
public class EventConfig extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    /** $column.columnComment */
    private Long id;

    /** $column.columnComment */

    private String packageId;

    /** $column.columnComment */

    private String packageToken;

    /** $column.columnComment */

    private String packagePixel;
    /** $column.columnComment */

    private String appName;

    private String url;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }
    public void setPackageId(String packageId)
    {
        this.packageId = packageId;
    }

    public String getPackageId()
    {
        return packageId;
    }
    public void setPackageToken(String packageToken)
    {
        this.packageToken = packageToken;
    }

    public String getPackageToken()
    {
        return packageToken;
    }
    public void setPackagePixel(String packagePixel)
    {
        this.packagePixel = packagePixel;
    }

    public String getPackagePixel()
    {
        return packagePixel;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("packageId", getPackageId())
            .append("packageToken", getPackageToken())
            .append("packagePixel", getPackagePixel())
            .append("appName", getAppName())
            .append("url", getUrl())
            .toString();
    }
}
