package org.jeecg.modules.packagemanagement;

import com.alibaba.fastjson2.JSONObject;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.application.apps.impl.ClientServiceImpl;
import org.jeecg.modules.packagemanagement.application.apps.risk.app2.*;
import org.jeecg.modules.packagemanagement.application.legacy.AESUtils;
import org.jeecg.modules.packagemanagement.application.legacy.ClientRiskPayloadCodec;
import org.jeecg.modules.packagemanagement.application.port.*;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import org.jeecg.modules.packagemanagement.controller.legacy.LegacyClientController;
import org.jeecg.modules.packagemanagement.domain.app.*;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.jeecg.modules.packagemanagement.PackageBusinessTest.inject;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PackageFourPathTest {
    static final String KEY = "1234567890123456";
    private App2InboundParser parser;
    private App2GlobalRiskEvaluator global;
    private App2PkgRiskRedisService redis;
    private App2RiskService risk;
    private App2ClientService client;
    private AppManager app;
    private AppAsnBlacklistMapper asns;
    private AppDeviceBrandBlacklistMapper brands;
    private AppCpuAbiBlacklistMapper abis;
    private AppDeviceNameBlacklistMapper names;
    private AppLogPort logs;
    private IAppManagerService apps;
    private GeoPort geo;

    @BeforeEach void setup() {
        PackageManagementProperties settings = new PackageManagementProperties();
        settings.setDefaultAesKey(KEY);
        parser = new App2InboundParser(); inject(parser,"settings",settings);
        global = new App2GlobalRiskEvaluator();
        asns=mock(AppAsnBlacklistMapper.class); brands=mock(AppDeviceBrandBlacklistMapper.class);
        abis=mock(AppCpuAbiBlacklistMapper.class); names=mock(AppDeviceNameBlacklistMapper.class);
        inject(global,"appAsnBlacklistMapper",asns); inject(global,"appDeviceBrandBlacklistMapper",brands);
        inject(global,"appCpuAbiBlacklistMapper",abis); inject(global,"appDeviceNameBlacklistMapper",names);
        redis=mock(App2PkgRiskRedisService.class); when(redis.evaluate(any(),any())).thenReturn(App2RiskResult.ok());
        risk=new App2RiskService(); inject(risk,"inboundParser",parser); inject(risk,"globalRiskEvaluator",global);
        inject(risk,"pkgRiskRedisService",redis); inject(risk,"installReferrerEvaluator",new App2InstallReferrerEvaluator());
        logs=mock(AppLogPort.class); apps=mock(IAppManagerService.class); geo=mock(GeoPort.class);
        client=new App2ClientService(); inject(client,"app2RiskService",risk); inject(client,"app2InboundParser",parser);
        inject(client,"settings",settings); inject(client,"geo",geo); inject(client,"appRepository",apps);
        inject(client,"esAppLogRepository",logs); inject(client,"clock",Clock.fixed(Instant.ofEpochMilli(1700000000000L),ZoneOffset.UTC));
        app=new AppManager(); app.setAppName(KEY); app.setStatus(3); app.setPackageCode("com.test");
        app.setWhetherUse(1); app.setMark("memo"); app.setUrl("https://example.invalid/play");
        when(apps.selectAppManagerByPackageName("com.test")).thenReturn(app);
        when(geo.getIpAddress(any())).thenReturn("192.0.2.1");
        when(geo.getFourPathCountryAndCountryCodeByIP(any())).thenReturn(Map.of("country","中国","countryCode","cn","status","false","asn","123"));
    }

    static String cipher(String value,String key,boolean hex) {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8), secret=key.getBytes(StandardCharsets.UTF_8);
        for(int i=0;i<bytes.length;i++) bytes[i]=(byte)(bytes[i]^secret[i%secret.length]);
        return hex?HexFormat.of().formatHex(bytes):Base64.getEncoder().encodeToString(bytes);
    }
    static MockHttpServletRequest request(String device,String key) {
        var r=new MockHttpServletRequest("POST","/a/b/c/d"); r.addHeader("x-pkg","com.test");
        r.setContentType("application/json"); r.setContent(JSONObject.toJSONString(Map.of("t",17,"p",cipher(device,key,false))).getBytes(StandardCharsets.UTF_8));
        return r;
    }
    private App2DevicePayload device(JSONObject ref) {
        return new App2DevicePayload("samsung","arm64-v8a","Phone","aid","gaid","Asia/Shanghai","cn",ref);
    }
    private JSONObject ref(Object inner) { return JSONObject.of("installVersion","1.0","installReferrer",inner); }

    @Test void parserCachesBodyForRiskAndDecodedLoggingAndAcceptsAliases() {
        var r=request("{\"b\":\"samsung\",\"abi\":\"arm64-v8a\",\"aid\":\" a \",\"gaid\":\"g\",\"t\":\"Asia/Shanghai\",\"f1\":\"cn\"}",KEY);
        var parsed=parser.parse(r,app);
        assertEquals("samsung",parsed.getPayload().getBrand()); assertEquals("arm64-v8a",parsed.getPayload().getCpuAbi());
        assertEquals("a",parsed.getPayload().getAid()); assertEquals("17",parsed.getClientTimestamp());
        assertEquals("Asia/Shanghai",parsed.getPayload().getTimezone());
        assertEquals("samsung",parser.logParamsForEs(r,app).getJSONObject("p").getString("b"));
        assertNotNull(parser.parse(r,app));
    }
    @Test void parserUsesAppKeyThenConfiguredFallbackAndDoesNotRequireTimestamp() {
        app.setAppName(null);
        var r=request("{\"f1\":\"cn\"}",KEY); assertNotNull(parser.parse(r,app));
        r=new MockHttpServletRequest(); r.addHeader("X-Pkg","com.test");
        r.setContent(JSONObject.toJSONString(Map.of("p",cipher("{\"f1\":\"cn\"}",KEY,true))).getBytes(StandardCharsets.UTF_8));
        assertNotNull(parser.parse(r,app)); assertNull(parser.parse(r,app).getClientTimestamp());
        inject(parser,"settings",new PackageManagementProperties());
        assertNotNull(parser.parse(request("{\"f1\":\"cn\"}",ClientRiskPayloadCodec.defaultXorSecret()),app));
    }
    @Test void malformedRequestsAndUnknownAppsReturnEmpty() {
        var noHeader=new MockHttpServletRequest(); noHeader.setContent("{}".getBytes(StandardCharsets.UTF_8));
        assertNull(client.execute(noHeader));
        var bad=request("{}",KEY); bad.setContent("not-json".getBytes(StandardCharsets.UTF_8)); assertNull(client.execute(bad));
        bad=request("{}",KEY); bad.setContent("{\"p\":\"%%%\"}".getBytes(StandardCharsets.UTF_8)); assertNull(client.execute(bad));
        when(apps.selectAppManagerByPackageName("com.test")).thenReturn(null); assertNull(client.execute(request("{}",KEY)));
    }
    @Test void realRouteDelegatesDecryptableAesResponseAndLogsOriginalUri() throws Exception {
        var service=new ClientServiceImpl(); inject(service,"app2ClientService",client);
        var mvc=MockMvcBuilders.standaloneSetup(new LegacyClientController(service)).build();
        app.setUrl("{\"link\":\"https://example.invalid/play\"}");
        String result=mvc.perform(post("/package-legacy/a/b/c/d").header("x-pkg","com.test").contentType("application/json")
            .content(JSONObject.toJSONString(Map.of("t",1,"p",cipher("{\"f1\":\"cn\"}",KEY,false)))))
            .andReturn().getResponse().getContentAsString();
        JSONObject plain=JSONObject.parseObject(AESUtils.decrypt(result,KEY));
        assertEquals(3,plain.getIntValue("code")); assertEquals(1,plain.getIntValue("byme")); assertEquals("memo",plain.getString("ps"));
        assertEquals("cn",JSONObject.parseObject(plain.getString("data")).getString("cityCode"));
        var saved=org.mockito.ArgumentCaptor.forClass(AppLog.class); verify(logs).save(saved.capture());
        assertEquals("/a/b/c/d",saved.getValue().getReqPath());
        assertNotNull(JSONObject.parseObject(saved.getValue().getReqParams()).getJSONObject("p"));
        assertEquals(1700000000000L,saved.getValue().getReqTime().getTime());
    }
    @Test void nullStatusAndGeoRejectBeforeGlobalOrRedis() {
        app.setStatus(null); assertNull(client.execute(request("{}",KEY))); verifyNoInteractions(redis);
        app.setStatus(3); app.setWhetherUs(1);
        when(geo.getFourPathCountryAndCountryCodeByIP(any())).thenReturn(Map.of("country","美国","status","true"));
        assertNull(client.execute(request("{}",KEY))); verifyNoInteractions(redis);
        app.setWhetherUs(0); app.setWhetherRestrict(1); app.setAreas("中国,巴西");
        assertNull(client.execute(request("{}",KEY))); verifyNoInteractions(redis);
    }
    @Test void statusModesAndRedisDenialKeepSourceOrder() {
        app.setStatus(1); assertNull(client.execute(request("{}",KEY)));
        String organic="{\"InstallReferrer\":{\"installVersion\":\"1\",\"installReferrer\":{\"utm_medium\":\"organic\",\"utm_source\":\"google-play\"}}}";
        assertNotNull(client.execute(request(organic,KEY)));
        app.setStatus(2); assertNull(client.execute(request(organic,KEY)));
        app.setStatus(3); assertNotNull(client.execute(request("{}",KEY)));
        when(redis.evaluate(any(),any())).thenReturn(App2RiskResult.reject("count exceeded"));
        assertNull(client.execute(request("{}",KEY)));
    }
    @Test void referrerValidationPreservesNestedKeyBlacklistAndQueryStringQuirk() {
        var evaluator=new App2InstallReferrerEvaluator();
        assertFalse(evaluator.evaluate(1,app,device(null)).isPass());
        assertFalse(evaluator.evaluate(1,app,device(JSONObject.of("installVersion","unknown"))).isPass());
        assertFalse(evaluator.evaluate(1,app,device(JSONObject.of("installVersion","1","googlePlayInstantParam",true))).isPass());
        assertFalse(evaluator.evaluate(1,app,device(ref(JSONObject.of("utm_medium","paid")))).isPass());
        assertTrue(evaluator.evaluate(2,app,device(ref("utm_medium=paid"))).isPass(),"source discards parsed query-string object");
        app.setReferrerBlacklistJson("{\"installReferrer\":{\"flag\":\"different value\"}}");
        assertFalse(evaluator.evaluate(1,app,device(ref(JSONObject.of("flag","actual value")))).isPass());
        assertTrue(evaluator.evaluate(1,app,device(ref(JSONObject.of("other","value")))).isPass());
    }
    @Test void allFourGlobalBlacklistCategoriesAndObserveAction() {
        var city=Map.of("asn","AS123"); var payload=device(null);
        AppAsnBlacklist asn=new AppAsnBlacklist(); asn.setAsn("123");
        when(asns.selectAppAsnBlacklistList(any())).thenReturn(List.of(asn));
        assertFalse(global.evaluate(app,payload,city).isPass()); asn.setAction("observe");
        assertTrue(global.evaluate(app,payload,city).isPass());
        var brand=new AppDeviceBrandBlacklist(); brand.setBrandKeyword("SAMSUNG"); brand.setMatchMode("contains");
        when(brands.selectAppDeviceBrandBlacklistList(any())).thenReturn(List.of(brand));
        assertFalse(global.evaluate(app,payload,city).isPass()); brand.setAction("observe");
        var abi=new AppCpuAbiBlacklist(); abi.setAbiKeyword("arm64-v8a"); abi.setMatchMode("equals");
        when(abis.selectAppCpuAbiBlacklistList(any())).thenReturn(List.of(abi));
        assertFalse(global.evaluate(app,payload,city).isPass()); abi.setAction("observe");
        var name=new AppDeviceNameBlacklist(); name.setDeviceNameKeyword("Phone"); name.setMatchMode("prefix");
        when(names.selectAppDeviceNameBlacklistList(any())).thenReturn(List.of(name));
        assertFalse(global.evaluate(app,payload,city).isPass()); name.setAction("observe");
        assertTrue(global.evaluate(app,payload,city).isPass());
    }
    @Test void keywordModesCaseSensitivityAndAsnNormalization() {
        assertTrue(App2KeywordMatcher.matches("Samsung ARM","samsung","prefix",0));
        assertTrue(App2KeywordMatcher.matches("Samsung ARM","ARM","suffix",1));
        assertTrue(App2KeywordMatcher.matches("Samsung","Samsung","eq",1));
        assertFalse(App2KeywordMatcher.matches("Samsung","samsung","equals",1));
        assertEquals("123",App2KeywordMatcher.normalizeAsnDigits("AS123"));
    }
    @Test void blankApplicationNameKeepsSourceDefaultAesFallback() throws Exception {
        app.setAppName("");
        var settings=new PackageManagementProperties(); inject(parser,"settings",settings); inject(client,"settings",settings);
        String key=ClientRiskPayloadCodec.defaultXorSecret();
        String response=(String)client.execute(request("{\"f1\":\"cn\"}",key));
        assertNotNull(response);
        assertEquals(app.getUrl(),JSONObject.parseObject(AESUtils.decrypt(response,key)).getString("data"));
    }
    @Test void geoAdapterPassesAsnToGlobalBlacklistWithSourceAsFieldParsing() {
        HttpPort http=mock(HttpPort.class); var settings=new PackageManagementProperties();
        settings.setGeoUrl("https://geo.example.invalid/{ip}");
        when(http.json(anyString(),anyString(),isNull(),anyMap())).thenReturn("{\"country\":\"中国\",\"countryCode\":\"CN\",\"as\":\"AS123 Local Network\",\"org\":\"Local\",\"isp\":\"Local\"}");
        var adapter=new org.jeecg.modules.packagemanagement.infrastructure.integration.GeoHttpAdapter(http,settings);
        var result=adapter.getFourPathCountryAndCountryCodeByIP("192.0.2.1");
        assertEquals("123",result.get("asn")); assertEquals("AS123 Local Network",result.get("as"));
        assertEquals("false",result.get("status"));
    }
    @Test void fourPathGeoMissingOptionalFieldsKeepsActualCountry() {
        HttpPort http=mock(HttpPort.class); var settings=new PackageManagementProperties();
        settings.setGeoUrl("https://geo.example.invalid/{ip}");
        when(http.json(anyString(),anyString(),isNull(),anyMap())).thenReturn("{\"country\":\"中国\",\"countryCode\":\"CN\"}");
        var adapter=new org.jeecg.modules.packagemanagement.infrastructure.integration.GeoHttpAdapter(http,settings);
        var result=adapter.getFourPathCountryAndCountryCodeByIP("192.0.2.1");
        assertEquals("中国",result.get("country")); assertEquals("false",result.get("status"));
    }
}
