package org.jeecg.modules.packagemanagement.application.port;
public interface PushPort {
void validateKeys(String publicKey,String privateKey) throws Exception;
void send(String publicKey,String privateKey,org.jeecg.modules.packagemanagement.domain.pwa.AppPwaSub subscription,byte[] payload) throws Exception;
}
