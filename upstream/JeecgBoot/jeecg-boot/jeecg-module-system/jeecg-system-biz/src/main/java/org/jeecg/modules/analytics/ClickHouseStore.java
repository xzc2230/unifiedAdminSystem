package org.jeecg.modules.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dedicated HTTP store; never participates in the MySQL dynamic datasource. */
public final class ClickHouseStore implements AutoCloseable {
    private final URI endpoint;
    private final HttpClient client;
    private final String writerAuth, readerAuth;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();
    private final java.util.concurrent.Semaphore queries = new java.util.concurrent.Semaphore(1);
    private final java.util.Set<java.util.concurrent.CompletableFuture<?>> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public ClickHouseStore(ClickHouseProperties p) {
        endpoint = URI.create(p.getEndpoint());
        boolean loopback = endpoint.getHost() != null && Set.of("127.0.0.1", "localhost", "[::1]").contains(endpoint.getHost());
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
                || !("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme()) && loopback)
                || p.getTimeoutSeconds() < 1 || p.getTimeoutSeconds() > 30) {
            throw new IllegalArgumentException("Invalid ClickHouse endpoint or timeout; use HTTPS or loopback HTTP");
        }
        writerAuth = auth(p.getWriterUser(), p.getWriterPassword());
        readerAuth = auth(p.getReaderUser(), p.getReaderPassword());
        timeout = Duration.ofSeconds(p.getTimeoutSeconds());
        client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static String auth(String user, String password) {
        if (user == null || user.isBlank() || user.contains(":") || password == null || password.isBlank()) {
            throw new IllegalArgumentException("ClickHouse credentials required");
        }
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    public void insert(List<ApiCallEvent> events) throws IOException, InterruptedException {
        if (events.isEmpty()) return;
        if (events.size() > 10000) throw new IllegalArgumentException("Analytics batch too large");
        var body = new StringBuilder();
        for (var event : events) body.append(json.writeValueAsString(event)).append('\n');
        request("query=" + encode("INSERT INTO jeecg_analytics.api_call FORMAT JSONEachRow"), body.toString(), writerAuth);
    }

    public List<Map<String, Object>> summary(long start, long end, String api) throws IOException, InterruptedException {
        if (start < 0 || end <= start || end - start > 86_400_000L || api == null || api.length() > 64) {
            throw new IllegalArgumentException("查询区间须为正向UTC毫秒时间，最长24小时；API ID最多64字符");
        }
        String sql = "SELECT api_id, method, count() AS calls, countIf(status >= 400) AS errors, "
                + "countIf(status >= 500) AS server_errors, avg(duration_ms) AS avg_ms, "
                + "quantileTDigest(0.95)(duration_ms) AS p95_ms, quantileTDigest(0.99)(duration_ms) AS p99_ms "
                + "FROM jeecg_analytics.api_call WHERE event_time >= fromUnixTimestamp64Milli({start:Int64}) "
                + "AND event_time < fromUnixTimestamp64Milli({end:Int64}) "
                + "AND ({api:String} = '' OR api_id = {api:String}) GROUP BY api_id, method "
                + "ORDER BY calls DESC LIMIT 1000 FORMAT JSONEachRow";
        if (!queries.tryAcquire()) throw new IOException("Analytics query busy");
        try {
            String reply = request("param_start=" + start + "&param_end=" + end + "&param_api=" + encode(api), sql, readerAuth);
            var rows = new java.util.ArrayList<Map<String, Object>>();
            for (String line : reply.split("\n")) if (!line.isBlank()) rows.add(json.readValue(line, new TypeReference<>() {}));
            return List.copyOf(rows);
        } finally { queries.release(); }
    }

    private String request(String query, String body, String auth) throws IOException, InterruptedException {
        URI uri = URI.create(endpoint.toString().replaceAll("/$", "") + "/?" + query);
        var req = HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization", auth)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        var future = client.sendAsync(req, ignored -> new LimitedBody());
        pending.add(future);
        if (closed) { pending.remove(future); future.cancel(true); throw new IOException("Analytics store closed"); }
        try {
            var response = future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw new IOException("ClickHouse request failed (HTTP " + response.statusCode() + ")");
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException error) {
            future.cancel(true); throw new IOException("ClickHouse request failed or timed out");
        } catch (InterruptedException error) {
            future.cancel(true); throw error;
        } catch (java.util.concurrent.CancellationException error) {
            throw new IOException("Analytics request cancelled");
        } finally {
            pending.remove(future);
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private java.util.concurrent.Flow.Subscription subscription;
        private long count;
        private boolean done;
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(java.util.concurrent.Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        public void onNext(List<java.nio.ByteBuffer> items) {
            if (done) return;
            for (var item : items) count += item.remaining();
            if (count > 2_097_152) {
                done = true; subscription.cancel(); delegate.onError(new IOException("ClickHouse response too large"));
            } else delegate.onNext(items);
        }
        public void onError(Throwable error) { if (!done) { done = true; delegate.onError(error); } }
        public void onComplete() { if (!done) { done = true; delegate.onComplete(); } }
    }

    @Override public void close() { closed = true; pending.forEach(future -> future.cancel(true)); }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
