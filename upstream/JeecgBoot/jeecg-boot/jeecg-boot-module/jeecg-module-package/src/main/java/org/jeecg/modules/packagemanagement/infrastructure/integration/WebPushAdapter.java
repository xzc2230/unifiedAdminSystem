package org.jeecg.modules.packagemanagement.infrastructure.integration;
import org.jeecg.modules.packagemanagement.application.port.PushPort;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwaSub;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import nl.martijndwars.webpush.*;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
public class WebPushAdapter implements PushPort  {
    private final PackageManagementProperties settings;
    public WebPushAdapter(PackageManagementProperties settings) {
        this.settings=settings;
    }
    private PushService prepare(String publicKey,String privateKey)throws Exception {
        if(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)==null)Security.addProvider(new BouncyCastleProvider());
        PushService service=new PushService();
        service.setPublicKey(publicKey);
        service.setPrivateKey(privateKey);
        if(!settings.getPushSubject().isBlank())service.setSubject(settings.getPushSubject());
        return service;
    }
    public void validateKeys(String publicKey,String privateKey)throws Exception {
        prepare(publicKey,privateKey);
    }
    public void send(String publicKey,String privateKey,AppPwaSub subscription,byte[] payload)throws Exception {
        prepare(publicKey,privateKey).send(new Notification(subscription.getEndpoint(),subscription.getP256dh(),subscription.getAuth(),payload));
    }
}
