package org.jeecg.modules.analytics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Best-effort telemetry: bounded queue, no request-thread IO, no ambiguous batch retry. */
public final class AnalyticsBuffer {
    @FunctionalInterface public interface Sink { void insert(List<ApiCallEvent> events) throws Exception; }
    private final ArrayBlockingQueue<ApiCallEvent> queue;
    private final int batchSize;
    private final Sink sink;
    private final AtomicLong accepted = new AtomicLong(), rejected = new AtomicLong();
    private final AtomicLong written = new AtomicLong(), failed = new AtomicLong();
    private final AtomicLong lastSuccess = new AtomicLong();
    private volatile int inFlight;

    public AnalyticsBuffer(int capacity, int batchSize, Sink sink) {
        if (capacity < 1 || capacity > 100000 || batchSize < 1 || batchSize > capacity || batchSize > 10000) {
            throw new IllegalArgumentException("Invalid analytics queue limits");
        }
        queue = new ArrayBlockingQueue<>(capacity); this.batchSize = batchSize; this.sink = sink;
    }

    public boolean offer(ApiCallEvent event) {
        boolean added = queue.offer(event);
        (added ? accepted : rejected).incrementAndGet();
        return added;
    }

    public int size() { return queue.size(); }

    public synchronized void flush() {
        var events = new ArrayList<ApiCallEvent>(batchSize);
        queue.drainTo(events, batchSize);
        if (events.isEmpty()) return;
        inFlight = events.size();
        try {
            sink.insert(List.copyOf(events)); written.addAndGet(events.size()); lastSuccess.set(System.currentTimeMillis());
        } catch (Exception ignored) {
            // An HTTP failure may have happened after commit: do not silently double count by retrying.
            failed.addAndGet(events.size());
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally { inFlight = 0; }
    }

    public Map<String, Object> status() {
        return Map.of("queued", queue.size(), "inFlight", inFlight, "accepted", accepted.get(),
                "rejected", rejected.get(), "written", written.get(), "failedOrUnknown", failed.get(),
                "lastSuccessEpochMillis", lastSuccess.get());
    }
}
