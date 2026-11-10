package org.jeecg.modules.packagemanagement.config;

/** Same keys as the analytics store. Request logs stay off until that store is enabled. */
@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "siteplatform.analytics.clickhouse")
public class RequestLogClickHouseProperties {
    private boolean enabled;
    private String endpoint = "http://127.0.0.1:18123";
    private String writerUser = "";
    private String writerPassword = "";
    private String readerUser = "";
    private String readerPassword = "";
    private int timeoutSeconds = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getWriterUser() { return writerUser; }
    public void setWriterUser(String writerUser) { this.writerUser = writerUser; }
    public String getWriterPassword() { return writerPassword; }
    public void setWriterPassword(String writerPassword) { this.writerPassword = writerPassword; }
    public String getReaderUser() { return readerUser; }
    public void setReaderUser(String readerUser) { this.readerUser = readerUser; }
    public String getReaderPassword() { return readerPassword; }
    public void setReaderPassword(String readerPassword) { this.readerPassword = readerPassword; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
