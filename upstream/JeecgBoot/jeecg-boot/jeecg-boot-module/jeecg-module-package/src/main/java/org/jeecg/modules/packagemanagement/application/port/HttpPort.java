package org.jeecg.modules.packagemanagement.application.port;
public interface HttpPort {
record Reply(int status,String body) {public boolean success(){return status>=200&&status<300;}}
 Reply exchange(String method,String url,String body,java.util.Map<String,String> headers);
 default String json(String method,String url,String body,java.util.Map<String,String> headers){Reply r=exchange(method,url,body,headers);if(!r.success())throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(502,"External HTTP status "+r.status());return r.body();}
}
