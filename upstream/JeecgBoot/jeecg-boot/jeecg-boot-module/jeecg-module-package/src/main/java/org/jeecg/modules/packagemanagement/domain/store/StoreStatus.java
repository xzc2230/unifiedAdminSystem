package org.jeecg.modules.packagemanagement.domain.store;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;
import java.util.List;

/**
 *
 * @author ruoyi
 * @date 2023-11-04
 */
public class StoreStatus extends PackageEntity {
    private static final long serialVersionUID = 1L;

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * 包名
     */

    private String packageCode;

    private String platform;

    private String appleAppId;

    private String storeRegion;

    /**
     * 市场链接
     */

    private String storeUrl;

    private Integer status;

    /** iOS App Store product status, such as 可供销售 or 等待审核. */
    private String productStatus;

    /** Comma-separated query values such as 1,ios:2. Not stored. */
    private String statusQuery;

    private List<Integer> statusIn;

    private List<Integer> iosStatusIn;

    private List<String> iosProductIn;

    private List<Integer> iosLegacyIn;

    public Date createDate;

    public Date successDate;

    public Date failDate;

//    success_email

    private String successEmail;
//fail_email

    private String failEmail;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPackageCode() {
        return packageCode;
    }

    public void setPackageCode(String packageCode) {
        this.packageCode = packageCode;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getAppleAppId() {
        return appleAppId;
    }

    public void setAppleAppId(String appleAppId) {
        this.appleAppId = appleAppId;
    }

    public String getStoreRegion() {
        return storeRegion;
    }

    public void setStoreRegion(String storeRegion) {
        this.storeRegion = storeRegion;
    }

    public String getStoreUrl() {
        return storeUrl;
    }

    public void setStoreUrl(String storeUrl) {
        this.storeUrl = storeUrl;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getProductStatus() {
        return productStatus;
    }

    public void setProductStatus(String productStatus) {
        this.productStatus = productStatus;
    }

    @JsonIgnore
    public String getStatusQuery() {
        return statusQuery;
    }

    public void setStatusQuery(String statusQuery) {
        this.statusQuery = statusQuery;
    }

    @JsonIgnore
    public List<Integer> getStatusIn() {
        return statusIn;
    }

    public void setStatusIn(List<Integer> statusIn) {
        this.statusIn = statusIn;
    }

    @JsonIgnore
    public List<Integer> getIosStatusIn() {
        return iosStatusIn;
    }

    public void setIosStatusIn(List<Integer> iosStatusIn) {
        this.iosStatusIn = iosStatusIn;
    }

    @JsonIgnore
    public List<String> getIosProductIn() {
        return iosProductIn;
    }

    public void setIosProductIn(List<String> iosProductIn) {
        this.iosProductIn = iosProductIn;
    }

    @JsonIgnore
    public List<Integer> getIosLegacyIn() {
        return iosLegacyIn;
    }

    public void setIosLegacyIn(List<Integer> iosLegacyIn) {
        this.iosLegacyIn = iosLegacyIn;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Date getSuccessDate() {
        return successDate;
    }

    public void setSuccessDate(Date successDate) {
        this.successDate = successDate;
    }

    public Date getFailDate() {
        return failDate;
    }

    public void setFailDate(Date failDate) {
        this.failDate = failDate;
    }

    public String getSuccessEmail() {
        return successEmail;
    }

    public void setSuccessEmail(String successEmail) {
        this.successEmail = successEmail;
    }

    public String getFailEmail() {
        return failEmail;
    }

    public void setFailEmail(String failEmail) {
        this.failEmail = failEmail;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("packageCode", getPackageCode())
                .append("platform", getPlatform())
                .append("appleAppId", getAppleAppId())
                .append("storeRegion", getStoreRegion())
                .append("storeUrl", getStoreUrl())
                .append("status", getStatus())
                .append("createTime", getCreateTime())
                .append("updateTime", getUpdateTime())
                .append("successEmail", getSuccessEmail())
                .append("failEmail", getFailEmail())
                .toString();
    }
}
