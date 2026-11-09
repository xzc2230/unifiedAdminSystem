package org.jeecg.modules.packagemanagement.infrastructure.integration;
import org.jeecg.modules.packagemanagement.application.port.MailPort;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;
public class SmtpMailAdapter implements MailPort  {
    private final PackageManagementProperties settings;
    public SmtpMailAdapter(PackageManagementProperties settings) {
        this.settings=settings;
    }
    public void sendEmail(String recipients,String subject,String body) {
        if(settings.getSmtpHost().isBlank())throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(503,"SMTP is not configured");
        try {
            Properties p=new Properties();
            p.setProperty("mail.smtp.host",settings.getSmtpHost());
            p.setProperty("mail.smtp.port",String.valueOf(settings.getSmtpPort()));
            p.setProperty("mail.smtp.auth","true");
            p.setProperty("mail.smtp.starttls.enable",String.valueOf(settings.isSmtpStartTls()));
            p.setProperty("mail.smtp.connectiontimeout","30000");
            p.setProperty("mail.smtp.timeout","30000");
            Session session=Session.getInstance(p,new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(settings.getSmtpUser(),settings.getSmtpPassword());
                }
            }
            );
            MimeMessage m=new MimeMessage(session);
            m.setFrom(new InternetAddress(settings.getSmtpFrom()));
            m.setRecipients(Message.RecipientType.TO,InternetAddress.parse(recipients));
            m.setSubject(subject,"UTF-8");
            m.setText(body,"UTF-8","html");
            m.setSentDate(new java.util.Date());
            Transport.send(m);
        }
        catch(Exception e) {
            throw new org.jeecg.modules.packagemanagement.exception.PackageFailure("SMTP send failed",e);
        }
    }
}
