package org.jeecg.modules.packagemanagement.controller.legacy;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jeecg.modules.packagemanagement.application.apps.ClientService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/package-legacy")
public class LegacyClientController {
    private final ClientService service;
    public LegacyClientController(ClientService service) { this.service = service; }
    private HttpServletRequest original(HttpServletRequest request) { return LegacyRequest.wrap(request); }

    @RequestMapping(value="/v2/api/user/login", produces="application/json;charset=UTF-8")
    public Object login(HttpServletRequest request) { return service.login(original(request)); }
    @RequestMapping(value="/v2/api/retry/{a}", produces="application/json;charset=UTF-8")
    public void redirected(HttpServletRequest request, HttpServletResponse response) { service.redirected(original(request), response); }
    @RequestMapping(value="/v2/api/banner/list/{a}", produces="application/json;charset=UTF-8")
    public Object banner(HttpServletRequest request) { return service.getBanner(original(request)); }
    @RequestMapping(value="/v2/api/check/url", produces="application/json;charset=UTF-8")
    public Object checkUrl(HttpServletRequest request) { return service.checkUrl(original(request)); }
    @RequestMapping(value="/system/{a}", produces="application/json;charset=UTF-8")
    public String system(HttpServletRequest request) { return Base64.getEncoder().encodeToString(JSON.toJSONString(service.api(original(request))).getBytes(StandardCharsets.UTF_8)); }
    @RequestMapping(value="/dynamic/{a}", produces="application/json;charset=UTF-8")
    public Object dynamic(HttpServletRequest request) { return service.dynamicAdj(original(request)); }
    @RequestMapping("/home/index/jsp")
    public Object home(HttpServletRequest request) { return service.base64Data(original(request)); }
    @RequestMapping("/app/start")
    public Object start(HttpServletRequest request) { return service.getUserInfo(original(request)); }
    @RequestMapping("/{a}/{b}/{c}")
    public Object triple(HttpServletRequest request) { return service.app1(original(request)); }
    @RequestMapping("/{l}/{a}")
    public Object doublePath(HttpServletRequest request) { return service.base64Data3(original(request)); }
    @RequestMapping("/{l}")
    public Object single(HttpServletRequest request) { return service.base64Data2(original(request)); }
}
