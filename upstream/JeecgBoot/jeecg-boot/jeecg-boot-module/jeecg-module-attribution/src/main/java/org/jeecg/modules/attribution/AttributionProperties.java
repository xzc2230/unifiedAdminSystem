package org.jeecg.modules.attribution;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "attribution.binding.v12")
public class AttributionProperties {
  private boolean enabled = false;
  private Set<String> enabledApplications = Set.of(), allowedHosts = Set.of();
  private String hmacKey;
  private Set<String> trustedEvidenceSources = Set.of();

  public Set<String> getTrustedEvidenceSources() {
    return trustedEvidenceSources;
  }

  public void setTrustedEvidenceSources(Set<String> v) {
    trustedEvidenceSources = Set.copyOf(v);
  }

  private int subjectRequestsPerMinute = 60,
      applicationRequestsPerMinute = 1000,
      adminRequestsPerMinute = 120,
      credentialTtlSeconds = 3600,
      tokenTtlSeconds = 600;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean v) {
    enabled = v;
  }

  public Set<String> getEnabledApplications() {
    return enabledApplications;
  }

  public void setEnabledApplications(Set<String> v) {
    enabledApplications = Set.copyOf(v);
  }

  public Set<String> getAllowedHosts() {
    return allowedHosts;
  }

  public void setAllowedHosts(Set<String> v) {
    allowedHosts = Set.copyOf(v);
  }

  public String getHmacKey() {
    return hmacKey;
  }

  public void setHmacKey(String v) {
    hmacKey = v;
  }

  public int getSubjectRequestsPerMinute() {
    return subjectRequestsPerMinute;
  }

  public void setSubjectRequestsPerMinute(int v) {
    subjectRequestsPerMinute = v;
  }

  public int getApplicationRequestsPerMinute() {
    return applicationRequestsPerMinute;
  }

  public void setApplicationRequestsPerMinute(int v) {
    applicationRequestsPerMinute = v;
  }

  public int getAdminRequestsPerMinute() {
    return adminRequestsPerMinute;
  }

  public void setAdminRequestsPerMinute(int v) {
    adminRequestsPerMinute = v;
  }

  public int getCredentialTtlSeconds() {
    return credentialTtlSeconds;
  }

  public void setCredentialTtlSeconds(int v) {
    credentialTtlSeconds = v;
  }

  public int getTokenTtlSeconds() {
    return tokenTtlSeconds;
  }

  public void setTokenTtlSeconds(int v) {
    tokenTtlSeconds = v;
  }
}
