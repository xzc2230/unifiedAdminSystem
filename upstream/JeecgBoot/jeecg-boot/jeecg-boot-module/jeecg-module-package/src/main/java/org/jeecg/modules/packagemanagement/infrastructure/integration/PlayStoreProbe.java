package org.jeecg.modules.packagemanagement.infrastructure.integration;

import org.jeecg.modules.packagemanagement.application.port.HttpPort;
import org.jeecg.modules.packagemanagement.application.port.StoreProbePort;
import org.jeecg.modules.packagemanagement.application.store.StoreDelistReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Google Play lookup. A 503 is a temporary frontend error, so it is retried once and never stored as 已下架. */
public final class PlayStoreProbe implements StoreProbePort {
    public static final long RETRY_DELAY_MILLIS = 60_000L;
    private static final Logger log = LoggerFactory.getLogger("package.store.probe");
    private final HttpPort http;
    private final long retryDelayMillis;
    private final Sleeper sleeper;

    public PlayStoreProbe(HttpPort http) {
        this(http, RETRY_DELAY_MILLIS, Thread::sleep);
    }

    public PlayStoreProbe(HttpPort http, long retryDelayMillis, Sleeper sleeper) {
        this.http = http;
        this.retryDelayMillis = retryDelayMillis;
        this.sleeper = sleeper;
    }

    @Override
    public Result getStatus(String url) {
        HttpPort.Reply reply = fetch(url);
        if (reply.status() == 503) {
            log.info("Play probe HTTP 503, retrying after {} ms url={}", retryDelayMillis, url);
            try {
                sleeper.sleep(retryDelayMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log.warn("Play probe 503 retry interrupted, status unchanged url={}", url);
                return null;
            }
            reply = fetch(url);
            if (reply.status() == 503) {
                log.warn("Play probe still HTTP 503 after retry, status unchanged url={}", url);
                return null;
            }
        }
        return reply.success()
                ? Result.listed()
                : Result.delisted(StoreDelistReason.http(reply.status(), reply.body()));
    }

    private HttpPort.Reply fetch(String url) {
        HttpPort.Reply reply = http.exchange("GET", url, null, Map.of());
        String body = reply.body() == null ? "" : reply.body();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String peek = new String(bytes, 0, Math.min(2048, bytes.length), StandardCharsets.UTF_8);
        log.info("Store probe url={} status={} headers={} body={}", url, reply.status(), reply.headers(), peek);
        return reply;
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
