package org.jeecg.modules.packagemanagement.config;
@org.springframework.boot.context.properties.ConfigurationProperties(prefix="jeecg.package-management")
public class PackageManagementProperties {
 private String defaultAesKey="";
 public String getDefaultAesKey(){return defaultAesKey;}
 public void setDefaultAesKey(String value){defaultAesKey=value;}
 private String geoUrl="";
 public String getGeoUrl(){return geoUrl;}
 public void setGeoUrl(String value){geoUrl=value;}
 private String geoApiKey="";
 public String getGeoApiKey(){return geoApiKey;}
 public void setGeoApiKey(String value){geoApiKey=value;}
 private String facebookBlockedUrl="";
 public String getFacebookBlockedUrl(){return facebookBlockedUrl;}
 public void setFacebookBlockedUrl(String value){facebookBlockedUrl=value;}
 private String landingFallbackUrl="https://www.bababagame.com/";
 public String getLandingFallbackUrl(){return landingFallbackUrl;}
 public void setLandingFallbackUrl(String value){landingFallbackUrl=value;}
 private String npmBaseUrl="";
 public String getNpmBaseUrl(){return npmBaseUrl;}
 public void setNpmBaseUrl(String value){npmBaseUrl=value;}
 private String npmIdentity="";
 public String getNpmIdentity(){return npmIdentity;}
 public void setNpmIdentity(String value){npmIdentity=value;}
 private String npmSecret="";
 public String getNpmSecret(){return npmSecret;}
 public void setNpmSecret(String value){npmSecret=value;}
 private String vapidUrl="";
 public String getVapidUrl(){return vapidUrl;}
 public void setVapidUrl(String value){vapidUrl=value;}
 private String pwaRoot="/data/pwa/";
 public String getPwaRoot(){return pwaRoot;}
 public void setPwaRoot(String value){pwaRoot=value;}
 private String smtpHost="";
 public String getSmtpHost(){return smtpHost;}
 public void setSmtpHost(String value){smtpHost=value;}
 private String smtpUser="";
 public String getSmtpUser(){return smtpUser;}
 public void setSmtpUser(String value){smtpUser=value;}
 private String smtpPassword="";
 public String getSmtpPassword(){return smtpPassword;}
 public void setSmtpPassword(String value){smtpPassword=value;}
 private String smtpFrom="";
 public String getSmtpFrom(){return smtpFrom;}
 public void setSmtpFrom(String value){smtpFrom=value;}
 private String storeRecipients="";
 public String getStoreRecipients(){return storeRecipients;}
 public void setStoreRecipients(String value){storeRecipients=value;}
 private String esUrl="";
 public String getEsUrl(){return esUrl;}
 public void setEsUrl(String value){esUrl=value;}
 private String esUsername="";
 public String getEsUsername(){return esUsername;}
 public void setEsUsername(String value){esUsername=value;}
 private String esPassword="";
 public String getEsPassword(){return esPassword;}
 public void setEsPassword(String value){esPassword=value;}
 private String facebookBaseUrl="";
 public String getFacebookBaseUrl(){return facebookBaseUrl;}
 public void setFacebookBaseUrl(String value){facebookBaseUrl=value;}
 private String pushSubject="";
 public String getPushSubject(){return pushSubject;}
 public void setPushSubject(String value){pushSubject=value;}
 private int smtpPort=587; public int getSmtpPort(){return smtpPort;} public void setSmtpPort(int port){smtpPort=port;}
 private boolean smtpStartTls=true; public boolean isSmtpStartTls(){return smtpStartTls;} public void setSmtpStartTls(boolean value){smtpStartTls=value;}
}
