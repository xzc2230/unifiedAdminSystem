package org.jeecg.modules.packagemanagement.domain.applog;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * 轻量级 UUIDv7 生成器（无第三方依赖）
 * 特点：
 * - 基于时间戳递增
 * - 线程安全
 * - 可排序
 * - 输出格式与 UUID 类似（xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx）
 */
public class UUIDv7Generator {

    // 全局安全随机数生成器
    private static final SecureRandom RANDOM = new SecureRandom();

    // 记录上次生成的时间戳，确保同毫秒内递增
    private static long lastTimestamp = 0L;
    private static long counter = 0L;
    private static final long COUNTER_MASK = (1L << 12) - 1; // 4096 序列号限制

    public static synchronized String generate() {
        long timestamp = Instant.now().toEpochMilli();

        if (timestamp == lastTimestamp) {
            counter = (counter + 1) & COUNTER_MASK;
            if (counter == 0) {
                // 等到下一个毫秒，防止溢出
                while (timestamp <= lastTimestamp) {
                    timestamp = Instant.now().toEpochMilli();
                }
            }
        } else {
            counter = 0;
        }

        lastTimestamp = timestamp;

        // 拼接时间戳和随机部分
        long timeHigh = (timestamp >> 16) & 0xFFFFFFFFL;
        long timeLow = timestamp & 0xFFFF;
        long randA = RANDOM.nextInt() & 0xFFFFFFFFL;
        long randB = RANDOM.nextInt() & 0xFFFFFFFFL;

        // 按 UUIDv7 格式拼接：timeHigh-timeLow-7xxx-xxxx-randB
        return String.format("%08x-%04x-7%03x-%04x-%012x",
                timeHigh, timeLow, (counter & 0xFFF),
                (randA & 0xFFFF), randB);
    }

}
