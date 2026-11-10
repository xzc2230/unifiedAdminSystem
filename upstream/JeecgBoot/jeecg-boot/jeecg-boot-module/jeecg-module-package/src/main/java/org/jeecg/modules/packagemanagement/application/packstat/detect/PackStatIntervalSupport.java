package org.jeecg.modules.packagemanagement.application.packstat.detect;

/**
 * Adaptive API interval: INVALID +1s capped at 60, success -1s floored at 0.
 */
public final class PackStatIntervalSupport
{
    public static final int MIN_SECONDS = 0;
    public static final int MAX_SECONDS = 60;

    private PackStatIntervalSupport()
    {
    }

    public static int nextIntervalSeconds(int current, boolean hadInvalidResult)
    {
        int safeCurrent = current < 0 ? 0 : current;
        if (hadInvalidResult)
        {
            return Math.min(MAX_SECONDS, safeCurrent + 1);
        }
        return Math.max(MIN_SECONDS, safeCurrent - 1);
    }
}
