package org.jeecg.modules.packagemanagement;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.application.packstat.detect.PackStatMailContentBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PackagePackStatMailContentBuilderTest
{
    @Test
    public void buildsDelistedSubject()
    {
        assertEquals("[应用商店监控] 状态变更：正常 → 已下架",
                PackStatMailContentBuilder.buildSubject());
    }

    @Test
    public void buildsDelistedBodyWithStoreAndRegion()
    {
        Date checkedAt = new Date(0L);
        String body = PackStatMailContentBuilder.buildDelistedBody(
                "https://apps.apple.com/cn/app/x/id1",
                "中国(cn)",
                checkedAt,
                "下架（404）");
        assertTrue(body.contains("应用商店链接：https://apps.apple.com/cn/app/x/id1"));
        assertTrue(body.contains("地区：中国(cn)"));
        assertTrue(body.contains("原状态：正常"));
        assertTrue(body.contains("新状态：已下架"));
        assertTrue(body.contains("本次检测结果：已下架"));
        assertTrue(body.contains("说明：下架（404）"));
        assertTrue(body.contains("—— 应用商店上架检测系统"));
    }
}
