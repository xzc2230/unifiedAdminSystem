package org.jeecg.modules.packagemanagement.application.packstat.detect;

import java.util.Date;
import java.util.Properties;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.jeecg.modules.packagemanagement.application.legacy.StringUtils;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStatDetectConfig;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStatRegion;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.PackStatRegionMapper;

/**
 * Send delist notification using packstat.mail SMTP (not MailUtils).
 */
@Component
public class PackStatMailNotifier
{
    private static final Logger log = LoggerFactory.getLogger(PackStatMailNotifier.class);

    @Autowired
    private PackStatMailProperties mailProperties;

    @Autowired
    private PackStatRegionMapper regionMapper;

    public void notifyDelisted(PackStat row, PackStatDetectConfig config, Date checkedAt, String detectMessage)
    {
        if (row == null)
        {
            return;
        }
        String global = config == null ? null : config.getNotifyEmail();
        String recipient = PackStatNotifyEmailSupport.resolve(row.getNotifyEmail(), global);
        if (StringUtils.isEmpty(recipient))
        {
            log.debug("packstat mail skip: no recipient for id={}", row.getId());
            return;
        }
        if (!mailProperties.isEnabled())
        {
            log.info("packstat mail disabled, skip delist notify id={} to={}", row.getId(), recipient);
            return;
        }
        if (StringUtils.isEmpty(mailProperties.getFrom()))
        {
            log.warn("packstat mail from empty, skip id={}", row.getId());
            return;
        }
        String[] to = PackStatNotifyEmailSupport.splitRecipients(recipient);
        if (to.length == 0)
        {
            return;
        }
        String subject = PackStatMailContentBuilder.buildSubject();
        String body = PackStatMailContentBuilder.buildDelistedBody(
                row.getStoreUrl(), formatRegionLabel(row.getRegionCode()), checkedAt, detectMessage);
        try
        {
            sendPlainText(to, subject, body);
            log.info("packstat delist mail sent id={} to={}", row.getId(), recipient);
        }
        catch (Exception ex)
        {
            log.error("packstat delist mail failed id={} to={}", row.getId(), recipient, ex);
        }
    }

    private String formatRegionLabel(String regionCode)
    {
        if (StringUtils.isEmpty(regionCode))
        {
            return "-";
        }
        PackStatRegion region = regionMapper.selectByCode(regionCode);
        if (region == null)
        {
            return regionCode;
        }
        String name = region.getNameCn() == null ? "" : region.getNameCn();
        return name + "(" + region.getCode() + ")";
    }

    private void sendPlainText(String[] to, String subject, String body) throws Exception
    {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", mailProperties.getHost());
        properties.put("mail.smtp.port", String.valueOf(mailProperties.getPort()));
        properties.put("mail.smtp.auth", "true");
        if (mailProperties.isSslEnable())
        {
            properties.put("mail.smtp.ssl.enable", "true");
            properties.put("mail.smtp.socketFactory.port", String.valueOf(mailProperties.getPort()));
            properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }
        final String username = mailProperties.getUsername();
        final String password = mailProperties.getPassword();
        Session session = Session.getInstance(properties, new Authenticator()
        {
            @Override
            protected PasswordAuthentication getPasswordAuthentication()
            {
                return new PasswordAuthentication(username, password);
            }
        });
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(mailProperties.getFrom()));
        InternetAddress[] addresses = new InternetAddress[to.length];
        for (int i = 0; i < to.length; i++)
        {
            addresses[i] = new InternetAddress(to[i]);
        }
        message.setRecipients(Message.RecipientType.TO, addresses);
        message.setSubject(subject, "UTF-8");
        message.setText(body, "UTF-8");
        message.setSentDate(new Date());
        Transport.send(message);
    }
}
