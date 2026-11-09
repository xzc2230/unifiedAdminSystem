package org.jeecg.modules.packagemanagement.domain.statistics;

import java.math.BigDecimal;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * 包统计对象 app_statistics
 *
 * @author ruoyi
 * @date 2023-11-04
 */
public class AppStatistics extends PackageEntity {
    private static final long serialVersionUID = 1L;

    /**
     * $column.columnComment
     */
    private Long id;

    /**
     * 包名
     */

    private String packageCode;

    /**
     * 注册总数
     */

    private Long reigisterNum;

    /**
     * 充值总数
     */

    private Long paymentNum;

    /**
     * 充值总额
     */

    private BigDecimal paymentTotal;

    /**
     * 主链接
     */

    private String url;

    /**
     * 子地区
     */

    private String subAreas;

    /**
     * 子链接
     */

    private String subUrl;

    /**
     * 用户
     */
    private Long userId;

    private Integer status;

    private Integer shunt;


    public Integer getShunt() {
        return shunt;
    }

    public void setShunt(Integer shunt) {
        this.shunt = shunt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setPackageCode(String packageCode) {
        this.packageCode = packageCode;
    }

    public String getPackageCode() {
        return packageCode;
    }

    public void setReigisterNum(Long reigisterNum) {
        this.reigisterNum = reigisterNum;
    }

    public Long getReigisterNum() {
        return reigisterNum;
    }

    public void setPaymentNum(Long paymentNum) {
        this.paymentNum = paymentNum;
    }

    public Long getPaymentNum() {
        return paymentNum;
    }

    public void setPaymentTotal(BigDecimal paymentTotal) {
        this.paymentTotal = paymentTotal;
    }

    public BigDecimal getPaymentTotal() {
        return paymentTotal;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setSubAreas(String subAreas) {
        this.subAreas = subAreas;
    }

    public String getSubAreas() {
        return subAreas;
    }

    public void setSubUrl(String subUrl) {
        this.subUrl = subUrl;
    }

    public String getSubUrl() {
        return subUrl;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("packageCode", getPackageCode())
                .append("reigisterNum", getReigisterNum())
                .append("paymentNum", getPaymentNum())
                .append("paymentTotal", getPaymentTotal())
                .append("url", getUrl())
                .append("subAreas", getSubAreas())
                .append("subUrl", getSubUrl())
                .append("createTime", getCreateTime())
                .append("updateTime", getUpdateTime())
                .append("userId", getUserId())
                .append("status", getStatus())
                .append("shunt", getShunt())
                .toString();
    }
}
