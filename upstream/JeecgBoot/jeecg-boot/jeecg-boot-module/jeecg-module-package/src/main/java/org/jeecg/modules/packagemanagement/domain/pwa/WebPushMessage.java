package org.jeecg.modules.packagemanagement.domain.pwa;

public class WebPushMessage {
    public String title;
    public String url;
    public String payload;
    public String appId;
    public WebPushMessage(String title, String url, String payload, String appId) {
        this.title = title;
        this.url = url;
        this.payload = payload;
        this.appId = appId;
    }

    @Override
    public String toString() {
        return "WebPushMessage{" +
                "title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", payload='" + payload + '\'' +
                ", appId='" + appId + '\'' +
                '}';
    }
}
