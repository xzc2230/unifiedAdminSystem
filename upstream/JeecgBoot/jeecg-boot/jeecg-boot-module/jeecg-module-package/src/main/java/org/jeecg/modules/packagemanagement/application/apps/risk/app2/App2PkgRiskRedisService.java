package org.jeecg.modules.packagemanagement.application.apps.risk.app2;


import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 单包风控：gaid/aid 关联计数与自动拉黑，数据存 Redis。
 * 键前缀见 App2RiskConstants.REDIS_PREFIX；窗口 TTL 见 app_manager.risk_window_seconds。
 */
@Service
public class App2PkgRiskRedisService {

    private static final String TIP_GAID_BLOCK = "gaid 已自动拉黑";
    private static final String TIP_AID_BLOCK = "aid 已自动拉黑";
    private static final String TIP_GAID_AID = "同一 gaid 关联过多 aid";
    private static final String TIP_AID_DEVICE = "同一 aid 对应过多设备信息";
    private static final String TIP_AID_GAID_SWITCH = "同一 aid 的 gaid 切换过多";
    private static final String REASON_LEGACY = "自动拉黑";

    @Autowired private StringRedisTemplate redisTemplate;

    /** 先查拉黑，再更新计数，超阈值写入 block 键（含原因） */
    public App2RiskResult evaluate(AppManager app, App2DevicePayload device) {
        String pkg = app.getPackageCode();
        if (StringUtils.isBlank(pkg)) { return App2RiskResult.ok(); }
        int windowSec = riskWindowSeconds(app);
        String gaid = device.getGaid();
        String aid = device.getAid();
        if (StringUtils.isNotBlank(gaid) && isBlocked(pkg, "gaid", gaid)) { return App2RiskResult.reject(TIP_GAID_BLOCK); }
        if (StringUtils.isNotBlank(aid) && isBlocked(pkg, "aid", aid)) { return App2RiskResult.reject(TIP_AID_BLOCK); }
        int gaidAidTh = threshold(app.getGaidAidThreshold());
        int aidDeviceTh = threshold(app.getAidDeviceThreshold());
        int aidGaidSwitchTh = threshold(app.getAidGaidSwitchThreshold());
        if (StringUtils.isNotBlank(gaid) && StringUtils.isNotBlank(aid)) {
            App2RiskResult r = trackGaidAid(pkg, gaid, aid, gaidAidTh, windowSec);
            if (!r.isPass()) { return r; }
        }
        String fingerprint = device.deviceFingerprint();
        if (StringUtils.isNotBlank(aid) && StringUtils.isNotBlank(fingerprint)) {
            App2RiskResult r = trackAidDevice(pkg, aid, fingerprint, aidDeviceTh, windowSec);
            if (!r.isPass()) { return r; }
        }
        if (StringUtils.isNotBlank(aid) && StringUtils.isNotBlank(gaid)) {
            return trackAidGaidSwitch(pkg, aid, gaid, aidGaidSwitchTh, windowSec);
        }
        return App2RiskResult.ok();
    }

    /** 后台：列出已自动拉黑的 gaid 或 aid（含拉黑原因） */
    public List<AutoBlockEntry> listAutoBlocked(String pkg, String type) {
        String pattern = App2RiskConstants.REDIS_PREFIX + "pkg:" + pkg + ":block:" + type + ":*";
        Collection<String> keys = redisTemplate.keys(pattern);
        List<AutoBlockEntry> rows = new ArrayList<>();
        if (keys == null) { return rows; }
        String prefix = App2RiskConstants.REDIS_PREFIX + "pkg:" + pkg + ":block:" + type + ":";
        for (String key : keys) {
            if (!key.startsWith(prefix)) { continue; }
            String value = key.substring(prefix.length());
            rows.add(new AutoBlockEntry(value, readBlockReason(key)));
        }
        return rows;
    }

    private String readBlockReason(String redisBlockKey) {
        Object raw = redisTemplate.opsForValue().get(redisBlockKey);
        if (raw == null) { return ""; }
        String reason = String.valueOf(raw).trim();
        if (StringUtils.isBlank(reason) || "1".equals(reason)) { return REASON_LEGACY; }
        return reason;
    }

    public static final class AutoBlockEntry {
        private final String value;
        private final String reason;

        public AutoBlockEntry(String value, String reason) {
            this.value = value;
            this.reason = reason;
        }

        public String getValue() { return value; }
        public String getReason() { return reason; }
    }

    /**
     * 后台：解除自动拉黑，并清空该 gaid/aid 在窗口内的关联计数。
     * 仅删 block 键时，计数仍超阈值会在下一次请求立刻再次拉黑。
     */
    public boolean removeAutoBlock(String pkg, String type, String value) {
        if (StringUtils.isAnyBlank(pkg, type, value)) { return false; }
        String v = value.trim();
        boolean removedBlock = Boolean.TRUE.equals(redisTemplate.delete(blockKey(pkg, type, v)));
        boolean clearedTrack = clearTrackingForUnblock(pkg, type, v);
        return removedBlock || clearedTrack;
    }

    private boolean clearTrackingForUnblock(String pkg, String type, String value) {
        boolean any = false;
        if ("gaid".equals(type)) {
            any = Boolean.TRUE.equals(redisTemplate.delete(gaidAidsKey(pkg, value)));
        } else if ("aid".equals(type)) {
            any = Boolean.TRUE.equals(redisTemplate.delete(aidDevicesKey(pkg, value)));
            any = Boolean.TRUE.equals(redisTemplate.delete(aidGaidSwitchesKey(pkg, value))) || any;
            any = Boolean.TRUE.equals(redisTemplate.delete(aidLastGaidKey(pkg, value))) || any;
        }
        return any;
    }

    private App2RiskResult trackGaidAid(String pkg, String gaid, String aid, int threshold, int windowSec) {
        String setKey = gaidAidsKey(pkg, gaid);
        redisTemplate.opsForSet().add(setKey, aid);
        touchTtl(setKey, windowSec);
        Long size = redisTemplate.opsForSet().size(setKey);
        if (size != null && size > threshold) { block(pkg, "gaid", gaid, TIP_GAID_AID, windowSec); return App2RiskResult.reject(TIP_GAID_AID); }
        return App2RiskResult.ok();
    }

    private App2RiskResult trackAidDevice(String pkg, String aid, String fingerprint, int threshold, int windowSec) {
        String setKey = aidDevicesKey(pkg, aid);
        redisTemplate.opsForSet().add(setKey, fingerprint);
        touchTtl(setKey, windowSec);
        Long size = redisTemplate.opsForSet().size(setKey);
        if (size != null && size > threshold) { block(pkg, "aid", aid, TIP_AID_DEVICE, windowSec); return App2RiskResult.reject(TIP_AID_DEVICE); }
        return App2RiskResult.ok();
    }

    private App2RiskResult trackAidGaidSwitch(String pkg, String aid, String gaid, int threshold, int windowSec) {
        String lastKey = aidLastGaidKey(pkg, aid);
        String countKey = aidGaidSwitchesKey(pkg, aid);
        Object lastObj = redisTemplate.opsForValue().get(lastKey);
        String last = lastObj == null ? "" : String.valueOf(lastObj);
        if (StringUtils.isNotBlank(last) && !last.equals(gaid)) {
            Long switches = redisTemplate.opsForValue().increment(countKey);
            touchTtl(countKey, windowSec);
            if (switches != null && switches > threshold) { block(pkg, "aid", aid, TIP_AID_GAID_SWITCH, windowSec); return App2RiskResult.reject(TIP_AID_GAID_SWITCH); }
        }
        redisTemplate.opsForValue().set(lastKey, gaid, windowSec, TimeUnit.SECONDS);
        return App2RiskResult.ok();
    }

    private void block(String pkg, String type, String value, String reason, int windowSec) {
        String r = StringUtils.isBlank(reason) ? REASON_LEGACY : reason.trim();
        redisTemplate.opsForValue().set(blockKey(pkg, type, value), r, windowSec, TimeUnit.SECONDS);
    }

    private boolean isBlocked(String pkg, String type, String value) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(blockKey(pkg, type, value)));
    }

    private static String blockKey(String pkg, String type, String value) {
        return App2RiskConstants.REDIS_PREFIX + "pkg:" + pkg + ":block:" + type + ":" + value;
    }

    private static String gaidAidsKey(String pkg, String gaid) {
        return App2RiskConstants.REDIS_PREFIX + "pkg:" + pkg + ":gaid:" + gaid + ":aids";
    }

    private static String aidDevicesKey(String pkg, String aid) {
        return App2RiskConstants.REDIS_PREFIX + "pkg:" + pkg + ":aid:" + aid + ":devices";
    }

    private static String aidGaidSwitchesKey(String pkg, String aid) {
        return App2RiskConstants.REDIS_PREFIX + "pkg:" + pkg + ":aid:" + aid + ":gaid_switches";
    }

    private static String aidLastGaidKey(String pkg, String aid) {
        return App2RiskConstants.REDIS_PREFIX + "pkg:" + pkg + ":aid:" + aid + ":last_gaid";
    }

    private void touchTtl(String key, int windowSec) { redisTemplate.expire(key, windowSec, TimeUnit.SECONDS); }

    private static int threshold(Integer configured) {
        return configured == null || configured < 1 ? App2RiskConstants.DEFAULT_THRESHOLD : configured;
    }

    private static int riskWindowSeconds(AppManager app) {
        Integer w = app.getRiskWindowSeconds();
        return w == null || w < 60 ? App2RiskConstants.DEFAULT_RISK_WINDOW_SECONDS : w;
    }
}
