package org.jeecg.modules.packagemanagement.domain.packstat;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * Single-row detection config (app_pack_stat_detect_config).
 */
public class PackStatDetectConfig extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer apiIntervalSeconds;
    private Integer threadCount;
    private Integer detectionFrequencyMinutes;
    /** Global fallback notify emails (comma-separated). */
    private String notifyEmail;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Integer getApiIntervalSeconds()
    {
        return apiIntervalSeconds;
    }

    public void setApiIntervalSeconds(Integer apiIntervalSeconds)
    {
        this.apiIntervalSeconds = apiIntervalSeconds;
    }

    public Integer getThreadCount()
    {
        return threadCount;
    }

    public void setThreadCount(Integer threadCount)
    {
        this.threadCount = threadCount;
    }

    public Integer getDetectionFrequencyMinutes()
    {
        return detectionFrequencyMinutes;
    }

    public void setDetectionFrequencyMinutes(Integer detectionFrequencyMinutes)
    {
        this.detectionFrequencyMinutes = detectionFrequencyMinutes;
    }

    public String getNotifyEmail()
    {
        return notifyEmail;
    }

    public void setNotifyEmail(String notifyEmail)
    {
        this.notifyEmail = notifyEmail;
    }

    public Date getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt)
    {
        this.updatedAt = updatedAt;
    }
}
