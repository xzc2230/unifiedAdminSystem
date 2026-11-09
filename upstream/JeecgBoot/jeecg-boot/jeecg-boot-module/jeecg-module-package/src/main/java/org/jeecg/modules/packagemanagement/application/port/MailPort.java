package org.jeecg.modules.packagemanagement.application.port;
public interface MailPort {
void sendEmail(String recipients,String subject,String body);
}
