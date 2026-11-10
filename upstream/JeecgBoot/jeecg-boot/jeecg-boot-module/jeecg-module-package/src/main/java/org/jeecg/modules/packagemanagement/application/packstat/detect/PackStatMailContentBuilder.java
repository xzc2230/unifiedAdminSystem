package org.jeecg.modules.packagemanagement.application.packstat.detect;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Mail subject/body copied from app-status-service DetectionMailNotifier style.
 */
public final class PackStatMailContentBuilder
{
    private PackStatMailContentBuilder()
    {
    }

    public static String buildSubject()
    {
        return "[应用商店监控] 状态变更：正常 → 已下架";
    }

    public static String buildDelistedBody(String storeUrl, String regionLabel, Date checkedAt, String message)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("检测结果已变更\n\n");
        sb.append("应用商店链接：").append(nullToEmpty(storeUrl)).append('\n');
        sb.append("地区：").append(nullToEmpty(regionLabel)).append('\n');
        sb.append("原状态：正常\n");
        sb.append("新状态：已下架\n");
        if (checkedAt != null)
        {
            sb.append("本次检测时间：")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(checkedAt))
                    .append('\n');
        }
        sb.append("本次检测结果：已下架\n");
        if (message != null && !message.trim().isEmpty())
        {
            sb.append("说明：").append(message.trim()).append('\n');
        }
        sb.append("\n—— 应用商店上架检测系统");
        return sb.toString();
    }

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }
}
