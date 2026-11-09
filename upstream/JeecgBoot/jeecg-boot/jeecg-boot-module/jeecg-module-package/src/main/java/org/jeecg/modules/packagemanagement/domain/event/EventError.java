package org.jeecg.modules.packagemanagement.domain.event;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * event_error对象 event_error
 *
 * @author ruoyi
 * @date 2024-04-16
 */
public class EventError extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    /** $column.columnComment */
    private Long id;

    /** $column.columnComment */

    private String errorPackage;

    /** $column.columnComment */

    private String errorMessage;

    /** $column.columnComment */

    private String errorContent;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }
    public void setErrorPackage(String errorPackage)
    {
        this.errorPackage = errorPackage;
    }

    public String getErrorPackage()
    {
        return errorPackage;
    }
    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }
    public void setErrorContent(String errorContent)
    {
        this.errorContent = errorContent;
    }

    public String getErrorContent()
    {
        return errorContent;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("errorPackage", getErrorPackage())
            .append("errorMessage", getErrorMessage())
            .append("errorContent", getErrorContent())
            .toString();
    }
}
