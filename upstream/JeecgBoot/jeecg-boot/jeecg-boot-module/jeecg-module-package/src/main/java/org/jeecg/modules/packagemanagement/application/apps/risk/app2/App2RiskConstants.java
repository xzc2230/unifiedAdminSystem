package org.jeecg.modules.packagemanagement.application.apps.risk.app2;

/**
 * app2（/{q}/{w}/{e}/{r}）风控常量；复用 {@code app_manager.status}。Redis {@link #REDIS_PREFIX}。
 */
public final class App2RiskConstants {

    /** 全关：直接返回空白 */
    public static final int STATUS_OFF = 0;
    /** 全开：全局 + 单包 Redis + InstallReferrer */
    public static final int STATUS_FULL = 1;
    /** 归因：全局 + 单包 */
    public static final int STATUS_ATTR = 2;
    /** 测试：跳过 InstallReferrer */
    public static final int STATUS_TEST = 3;

    public static final String HEADER_PKG = "x-pkg";
    public static final String REDIS_PREFIX = "package:legacy:app2:";
    public static final int DEFAULT_THRESHOLD = 1;
    public static final int DEFAULT_RISK_WINDOW_SECONDS = 86400;

    private App2RiskConstants() {}

    public static boolean isReferrerMode(int riskMode) {
        return riskMode == STATUS_FULL || riskMode == STATUS_ATTR;
    }

    public static boolean skipsReferrer(int riskMode) {
        return riskMode == STATUS_TEST;
    }
}
