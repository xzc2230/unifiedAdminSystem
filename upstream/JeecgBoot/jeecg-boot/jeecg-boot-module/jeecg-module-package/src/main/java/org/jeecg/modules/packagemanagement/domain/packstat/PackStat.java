package org.jeecg.modules.packagemanagement.domain.packstat;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * Isolated pack statistics row (app_pack_stat).
 */
public class PackStat extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer tenantId;
    private String ownerUserId;
    public Integer getTenantId(){return tenantId;}
    public void setTenantId(Integer value){tenantId=value;}
    public String getOwnerUserId(){return ownerUserId;}
    public void setOwnerUserId(String value){ownerUserId=value;}
    private String platform;
    private String storeUrl;
    private String regionCode;
    private String customer;
    private Integer isReplenish;
    private String replenishUrl;
    private String packer;
    /** Comma-separated notify emails. */
    private String notifyEmail;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date passDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date customerUseDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dropDate;

    private String designer;
    private Integer surviveDays;
    private Integer useDays;

    /** Query only: yyyy-MM filter on pass_date. */
    private String passMonth;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getStoreUrl()
    {
        return storeUrl;
    }

    public void setStoreUrl(String storeUrl)
    {
        this.storeUrl = storeUrl;
    }

    public String getRegionCode()
    {
        return regionCode;
    }

    public void setRegionCode(String regionCode)
    {
        this.regionCode = regionCode;
    }

    public String getCustomer()
    {
        return customer;
    }

    public void setCustomer(String customer)
    {
        this.customer = customer;
    }

    public Integer getIsReplenish()
    {
        return isReplenish;
    }

    public void setIsReplenish(Integer isReplenish)
    {
        this.isReplenish = isReplenish;
    }

    public String getReplenishUrl()
    {
        return replenishUrl;
    }

    public void setReplenishUrl(String replenishUrl)
    {
        this.replenishUrl = replenishUrl;
    }

    public String getPacker()
    {
        return packer;
    }

    public void setPacker(String packer)
    {
        this.packer = packer;
    }

    public String getNotifyEmail()
    {
        return notifyEmail;
    }

    public void setNotifyEmail(String notifyEmail)
    {
        this.notifyEmail = notifyEmail;
    }

    public Date getPassDate()
    {
        return passDate;
    }

    public void setPassDate(Date passDate)
    {
        this.passDate = passDate;
    }

    public Date getCustomerUseDate()
    {
        return customerUseDate;
    }

    public void setCustomerUseDate(Date customerUseDate)
    {
        this.customerUseDate = customerUseDate;
    }

    public Date getDropDate()
    {
        return dropDate;
    }

    public void setDropDate(Date dropDate)
    {
        this.dropDate = dropDate;
    }

    public String getDesigner()
    {
        return designer;
    }

    public void setDesigner(String designer)
    {
        this.designer = designer;
    }

    public Integer getSurviveDays()
    {
        return surviveDays;
    }

    public void setSurviveDays(Integer surviveDays)
    {
        this.surviveDays = surviveDays;
    }

    public Integer getUseDays()
    {
        return useDays;
    }

    public void setUseDays(Integer useDays)
    {
        this.useDays = useDays;
    }

    public String getPassMonth()
    {
        return passMonth;
    }

    public void setPassMonth(String passMonth)
    {
        this.passMonth = passMonth;
    }
}
