package org.jeecg.modules.packagemanagement.domain.domain;

import java.util.Date;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import com.fasterxml.jackson.annotation.JsonFormat;

/** A registrable client domain attached to Cloudflare and, once ready, to origin nginx. */
public class ClientDomain extends PackageEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String apex;
    /** pending, ready, or failed. */
    private String status;
    private String nameservers;
    private String zoneId;
    private String errorMessage;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getApex() { return apex; }
    public void setApex(String apex) { this.apex = apex; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNameservers() { return nameservers; }
    public void setNameservers(String nameservers) { this.nameservers = nameservers; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
}
