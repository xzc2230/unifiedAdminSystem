package org.jeecg.modules.packagemanagement.domain.applog;

import com.fasterxml.jackson.annotation.JsonFormat;

import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;

/**
 * 包日志对象 app_log
 *
 * @author ruoyi
 * @date 2024-07-16
 */

public class AppLog extends PackageEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */


    private String id = initId();

    /**
     * 包名
     */

    private String packageName;

    /**
     * 请求地址
     */

    private String reqPath;

    /**
     * 请求IP
     */

    private String reqIp;

    /**
     * 请求参数
     */

    private String reqParams;

    /**
     * 请求头
     */

    private String reqHeards;

    /**
     * 请求方式
     */

    private String reqMethod;

    /**
     * 请求结果
     */

    private String resultMsg;

    /**
     * 异常信息
     */

    private String errorMsg;

    /**
     * 请求时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")

    private Date reqTime;

    /**
     * 开始请求时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startReqTime;

    /**
     * 结束请求时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endReqTime;

    /**
     * 是否是手机
     */

    private Integer isMobile;

    public String initId() {

        return UUIDv7Generator.generate();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setReqPath(String reqPath) {
        this.reqPath = reqPath;
    }

    public String getReqPath() {
        return reqPath;
    }

    public void setReqIp(String reqIp) {
        this.reqIp = reqIp;
    }

    public String getReqIp() {
        return reqIp;
    }

    public void setReqParams(String reqParams) {
        this.reqParams = reqParams;
    }

    public String getReqParams() {
        return reqParams;
    }

    public void setReqHeards(String reqHeards) {
        this.reqHeards = reqHeards;
    }

    public String getReqHeards() {
        return reqHeards;
    }

    public void setReqMethod(String reqMethod) {
        this.reqMethod = reqMethod;
    }

    public String getReqMethod() {
        return reqMethod;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setReqTime(Date reqTime) {
        this.reqTime = reqTime;
    }

    public Date getReqTime() {
        return reqTime;
    }

    public void setIsMobile(Integer isMobile) {
        this.isMobile = isMobile;
    }

    public Integer getIsMobile() {
        return isMobile;
    }

    public Date getStartReqTime() {
        return startReqTime;
    }

    public void setStartReqTime(Date startReqTime) {
        this.startReqTime = startReqTime;
    }

    public Date getEndReqTime() {
        return endReqTime;
    }

    public void setEndReqTime(Date endReqTime) {
        this.endReqTime = endReqTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("packageName", getPackageName())
                .append("reqPath", getReqPath())
                .append("reqIp", getReqIp())
                .append("reqParams", getReqParams())
                .append("reqHeards", getReqHeards())
                .append("reqMethod", getReqMethod())
                .append("resultMsg", getResultMsg())
                .append("errorMsg", getErrorMsg())
                .append("reqTime", getReqTime())
                .append("startReqTime", getStartReqTime())
                .append("endReqTime", getEndReqTime())
                .append("isMobile", getIsMobile())
                .toString();
    }
}
