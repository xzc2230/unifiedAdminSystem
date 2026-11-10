package org.jeecg.modules.packagemanagement.application.packstat.detect;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SMTP settings migrated from app-status-service application-mail.yml.
 * Prefix packstat.mail — isolated from MailUtils / store module.
 */
@Component
@ConfigurationProperties(prefix = "jeecg.package-management.packstat.mail")
public class PackStatMailProperties
{
    private boolean enabled = false;
    private String from = "";
    private String host = "";
    private int port = 465;
    private String username = "";
    private String password = "";
    private boolean sslEnable = true;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getFrom()
    {
        return from;
    }

    public void setFrom(String from)
    {
        this.from = from;
    }

    public String getHost()
    {
        return host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public boolean isSslEnable()
    {
        return sslEnable;
    }

    public void setSslEnable(boolean sslEnable)
    {
        this.sslEnable = sslEnable;
    }
}
