package org.jeecg.modules.packagemanagement.application.port;
public interface PwaGateway {
String generateVapidKeys(); String createHost(String body); void updateHost(String linkId,String body); void deleteHost(String linkId);
}
