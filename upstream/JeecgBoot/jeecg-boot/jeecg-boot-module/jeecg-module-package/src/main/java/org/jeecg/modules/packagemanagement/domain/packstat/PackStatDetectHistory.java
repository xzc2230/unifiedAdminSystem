package org.jeecg.modules.packagemanagement.domain.packstat;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * Detection history row (app_pack_stat_detect_history).
 */
public class PackStatDetectHistory extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long statId;
    private String storeUrl;
    private String region;
    private String detectResult;
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date checkedAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getStatId()
    {
        return statId;
    }

    public void setStatId(Long statId)
    {
        this.statId = statId;
    }

    public String getStoreUrl()
    {
        return storeUrl;
    }

    public void setStoreUrl(String storeUrl)
    {
        this.storeUrl = storeUrl;
    }

    public String getRegion()
    {
        return region;
    }

    public void setRegion(String region)
    {
        this.region = region;
    }

    public String getDetectResult()
    {
        return detectResult;
    }

    public void setDetectResult(String detectResult)
    {
        this.detectResult = detectResult;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public Date getCheckedAt()
    {
        return checkedAt;
    }

    public void setCheckedAt(Date checkedAt)
    {
        this.checkedAt = checkedAt;
    }
}
