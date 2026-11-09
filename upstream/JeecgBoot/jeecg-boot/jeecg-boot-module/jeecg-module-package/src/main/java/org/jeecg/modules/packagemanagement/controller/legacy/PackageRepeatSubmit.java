package org.jeecg.modules.packagemanagement.controller.legacy;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PackageRepeatSubmit {
    private final StringRedisTemplate redis; private final Clock clock;
    public PackageRepeatSubmit(StringRedisTemplate redis,@org.springframework.beans.factory.annotation.Qualifier("packageClock") Clock clock){this.redis=redis;this.clock=clock;}
    public boolean repeated(HttpServletRequest request,String body) {
        String uri=LegacyRequest.wrap(request).getRequestURI();
        String token=request.getHeader("Authorization");token=token==null?"":token.trim();
        String params=body==null||body.isEmpty()?JSON.toJSONString(request.getParameterMap()):body;
        String key="pkg:repeat_submit:"+uri+token;
        String cached=redis.opsForValue().get(key);long now=clock.millis();
        if(cached!=null) {
            var previous=JSON.parseObject(cached);
            if(params.equals(previous.getString("params"))&&now-previous.getLongValue("time")<1000)return true;
        }
        redis.opsForValue().set(key,JSON.toJSONString(Map.of("params",params,"time",now)),1000,TimeUnit.MILLISECONDS);
        return false;
    }
}
