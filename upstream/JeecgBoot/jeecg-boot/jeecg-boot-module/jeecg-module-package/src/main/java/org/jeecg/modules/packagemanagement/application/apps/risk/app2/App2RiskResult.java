package org.jeecg.modules.packagemanagement.application.apps.risk.app2;

/**
 * 单步风控判定结果：仅「放行」或「拦截」两种终态。
 */
public final class App2RiskResult {

    /** 是否放行 */
    private final boolean pass;
    /** 拦截时的简短原因（写入 ES 日志）；放行时为 null */
    private final String rejectTip;

    private App2RiskResult(boolean pass, String rejectTip) {
        this.pass = pass;
        this.rejectTip = rejectTip;
    }

    public boolean isPass() {
        return pass;
    }

    public String getRejectTip() {
        return rejectTip;
    }

    /** 风控通过 */
    public static App2RiskResult ok() {
        return new App2RiskResult(true, null);
    }

    /** 风控拦截 */
    public static App2RiskResult reject(String tip) {
        return new App2RiskResult(false, tip);
    }
}
