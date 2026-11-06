package org.jeecg.modules.analytics;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class AnalyticsService {
    private final ClickHouseStore store;
    private final AnalyticsBuffer buffer;
    private final ScheduledExecutorService worker;
    private volatile boolean closed;

    public AnalyticsService(ClickHouseProperties properties) {
        if (!properties.isEnabled()) { store = null; buffer = null; worker = null; return; }
        if (properties.getFlushSeconds() < 1 || properties.getFlushSeconds() > 60) {
            throw new IllegalArgumentException("Analytics flush interval must be 1..60 seconds");
        }
        store = new ClickHouseStore(properties);
        buffer = new AnalyticsBuffer(properties.getCapacity(), properties.getBatchSize(), store::insert);
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "clickhouse-analytics"); t.setDaemon(true); return t;
        });
        // Flush full batches promptly; the time threshold also drains sparse traffic.
        // One worker and fixed delay keep storage concurrency and scheduled work bounded.
        worker.scheduleWithFixedDelay(new Runnable() {
            private long lastFlush = System.nanoTime();
            public void run() {
                if (buffer.size() >= properties.getBatchSize()
                        || System.nanoTime() - lastFlush >= TimeUnit.SECONDS.toNanos(properties.getFlushSeconds())) {
                    buffer.flush(); lastFlush = System.nanoTime();
                }
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    public void record(String apiId, String method, int status, long elapsed) {
        if (buffer != null && !closed) buffer.offer(ApiCallEvent.create(apiId, method, status, elapsed));
    }

    public Map<String, Object> status() {
        var result = new HashMap<String, Object>();
        result.put("enabled", store != null); result.put("delivery", "best-effort-memory");
        result.put("scope", "platform-admin-global-api-metadata");
        if (buffer != null) result.putAll(buffer.status());
        return result;
    }

    public List<Map<String, Object>> summary(long start, long end, String api) throws java.io.IOException, InterruptedException {
        if (store == null) throw new IllegalStateException("ClickHouse统计未启用");
        return store.summary(start, end, api);
    }

    @PreDestroy public void close() {
        closed = true;
        if (worker != null) worker.shutdownNow();
        if (store != null) store.close();
        // In-memory pending events may be lost on shutdown. This is not an audit ledger.
    }
}
