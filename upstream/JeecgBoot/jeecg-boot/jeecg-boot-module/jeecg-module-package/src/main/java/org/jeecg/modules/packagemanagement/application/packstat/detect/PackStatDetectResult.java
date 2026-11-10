package org.jeecg.modules.packagemanagement.application.packstat.detect;

/**
 * HTTP store-page probe outcome. 404=DELISTED, 200/301/302=NORMAL, else INVALID.
 */
public enum PackStatDetectResult
{
    NORMAL,
    DELISTED,
    INVALID;

    public static PackStatDetectResult fromHttpStatus(int status)
    {
        if (status == 404)
        {
            return DELISTED;
        }
        if (status == 200 || status == 301 || status == 302)
        {
            return NORMAL;
        }
        return INVALID;
    }
}
