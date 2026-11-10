package org.jeecg.modules.packagemanagement.infrastructure.integration;
import org.jeecg.modules.packagemanagement.application.port.HttpPort;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
public class JdkHttpAdapter implements HttpPort  {
    private volatile HttpClient client;
    private HttpClient client() {
        HttpClient current=client;
        if(current==null) {
            synchronized(this) {
                current=client;
                if(current==null) {
                    current=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();
                    client=current;
                }
            }
        }
        return current;
    }
    public Reply exchange(String method,String url,String body,Map<String,String> headers) {
        if(url==null||url.isBlank())throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(503,"Integration endpoint is not configured");
        try {
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120));
            headers.forEach(b::header);
            b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> r=client().send(b.build(),HttpResponse.BodyHandlers.ofString());
            var responseHeaders=new java.util.LinkedHashMap<String,String>();
            r.headers().map().forEach((key,values)->responseHeaders.put(key,String.join(",",values)));
            return new Reply(r.statusCode(),r.body(),responseHeaders);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new org.jeecg.modules.packagemanagement.exception.PackageFailure("External HTTP interrupted",e);
        }
        catch(Exception e) {
            throw new org.jeecg.modules.packagemanagement.exception.PackageFailure("External HTTP failed",e);
        }
    }
}
