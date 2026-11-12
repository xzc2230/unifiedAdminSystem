package org.jeecg.modules.packagemanagement.application.apps.risk.app2;

import org.jeecg.modules.packagemanagement.application.apps.support.BlockedProviders;
import org.jeecg.modules.packagemanagement.application.apps.support.PackageCountryRegions;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * app2 风控总入口（/{q}/{w}/{e}/{r}）。
 * <p>
 * 按固定顺序执行，避免逻辑散落在 Controller / Service 多处：
 * <ol>
 *   <li>解析入参（x-pkg + t/p）</li>
 *   <li>风控全关（status=0）</li>
 *   <li>IP 维度：禁美、地区白名单</li>
 *   <li>全局：ASN/品牌/ABI/设备型号黑名单（时区/SIM 白名单暂不使用）</li>
 *   <li>单包：Redis 关联计数与自动拉黑</li>
 *   <li>InstallReferrer（全开/归因；调试模式跳过）</li>
 * </ol>
 */
@Service
public class App2RiskService {

    private static final String TIP_PARSE_FAIL = "请求参数无效";
    private static final String TIP_RISK_MODE_OFF = "风控全关";
    private static final String TIP_US = "禁用美国及google、apple、amazon等运营商";
    private static final String TIP_AREA = "不在白名单";

    @Autowired
    private App2InboundParser inboundParser;
    @Autowired
    private App2GlobalRiskEvaluator globalRiskEvaluator;
    @Autowired
    private App2PkgRiskRedisService pkgRiskRedisService;
    @Autowired
    private App2InstallReferrerEvaluator installReferrerEvaluator;

    /**
     * 执行完整风控链路。
     *
     * @param request 客户端 HTTP 请求
     * @param app     已匹配的包配置（查包失败时调用方不应传 null）
     * @param cityMap IP 解析结果，含 country、countryCode、asn、status 等
     */
    public Evaluation evaluate(HttpServletRequest request, AppManager app, Map<String, String> cityMap) {
        App2InboundParser.Parsed parsed = inboundParser.parse(request, app);
        if (parsed == null) {
            return Evaluation.reject(null, TIP_PARSE_FAIL);
        }
        int status = app.getStatus() == null ? 0 : app.getStatus();
        if (status == App2RiskConstants.STATUS_OFF) {
            return Evaluation.reject(parsed, TIP_RISK_MODE_OFF);
        }

        App2RiskResult geo = checkGeo(app, cityMap);
        if (!geo.isPass()) {
            return Evaluation.reject(parsed, geo.getRejectTip());
        }

        App2RiskResult global = globalRiskEvaluator.evaluate(app, parsed.getPayload(), cityMap);
        if (!global.isPass()) {
            return Evaluation.reject(parsed, global.getRejectTip());
        }

        App2RiskResult pkg = pkgRiskRedisService.evaluate(app, parsed.getPayload());
        if (!pkg.isPass()) {
            return Evaluation.reject(parsed, pkg.getRejectTip());
        }

        if (App2RiskConstants.skipsReferrer(status)) {
            return Evaluation.pass(parsed);
        }

        if (App2RiskConstants.isReferrerMode(status)) {
            App2RiskResult ref = installReferrerEvaluator.evaluate(status, app, parsed.getPayload());
            if (!ref.isPass()) {
                return Evaluation.reject(parsed, ref.getRejectTip());
            }
        }

        return Evaluation.pass(parsed);
    }

    /**
     * 校验 IP 地区：禁美（whetherUs）、地区白名单（whetherRestrict）。
     */
    private App2RiskResult checkGeo(AppManager app, Map<String, String> cityMap) {
        String country = PackageCountryRegions.geoCode(cityMap);
        if (app.getWhetherUs() != null && app.getWhetherUs() == 1) {
            if ("true".equals(cityMap.get("status")) || "us".equals(country)) {
                return App2RiskResult.reject(TIP_US);
            }
        }
        String providers = BlockedProviders.hit(app.getBlockedProviders(), cityMap == null ? "" : cityMap.get("providers"));
        if (!providers.isEmpty()) {
            return App2RiskResult.reject("禁用运营商：" + providers);
        }
        if (app.getWhetherRestrict() != null && app.getWhetherRestrict() == 1) {
            String areas = app.getAreas();
            if (StringUtils.isBlank(areas)) {
                return App2RiskResult.reject(TIP_AREA);
            }
            if (!PackageCountryRegions.matches(areas, country)) {
                return App2RiskResult.reject(TIP_AREA);
            }
        }
        return App2RiskResult.ok();
    }

    /**
     * 供 {@link org.jeecg.modules.packagemanagement.application.apps.impl.ClientServiceImpl#app2} 使用的判定结果。
     */
    public static final class Evaluation {
        private final boolean pass;
        private final App2InboundParser.Parsed parsed;
        private final String rejectTip;

        private Evaluation(boolean pass, App2InboundParser.Parsed parsed, String rejectTip) {
            this.pass = pass;
            this.parsed = parsed;
            this.rejectTip = rejectTip;
        }

        public static Evaluation pass(App2InboundParser.Parsed parsed) {
            return new Evaluation(true, parsed, null);
        }

        public static Evaluation reject(App2InboundParser.Parsed parsed, String tip) {
            return new Evaluation(false, parsed, tip);
        }

        public boolean isPass() {
            return pass;
        }

        public App2InboundParser.Parsed getParsed() {
            return parsed;
        }

        public String getRejectTip() {
            return rejectTip;
        }
    }
}
