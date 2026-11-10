package org.jeecg.modules.packagemanagement.domain.packstat;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * Listing region row (app_pack_stat_region).
 */
public class PackStatRegion extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String nameCn;
    private Integer enabled;
    private Integer sortOrder;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getNameCn()
    {
        return nameCn;
    }

    public void setNameCn(String nameCn)
    {
        this.nameCn = nameCn;
    }

    public Integer getEnabled()
    {
        return enabled;
    }

    public void setEnabled(Integer enabled)
    {
        this.enabled = enabled;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }
}
