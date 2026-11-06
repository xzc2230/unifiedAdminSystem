package org.jeecg.modules.analytics;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ClickHouseStoreTest {
    static ClickHouseProperties properties(String endpoint) {
        var p = new ClickHouseProperties();
        p.setEnabled(true); p.setEndpoint(endpoint);
        p.setWriterUser("writer"); p.setWriterPassword("writer-secret");
        p.setReaderUser("reader"); p.setReaderPassword("reader-secret");
        return p;
    }

    @Test void batchUsesSanitizedJsonAndSeparateCredentialsAndFixedQuery() throws Exception {
        var body = new AtomicReference<String>();
        var uri = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            uri.set(exchange.getRequestURI().toString());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] reply = new byte[0]; exchange.sendResponseHeaders(200, -1);
            try (var out = exchange.getResponseBody()) { out.write(reply); }
        });
        server.start();
        try {
            var store = new ClickHouseStore(properties("http://127.0.0.1:" + server.getAddress().getPort()));
            store.insert(List.of(ApiCallEvent.create("api", "GET", 200, 12)));
            assertTrue(body.get().contains("\"api_id\":\"api\""));
            assertFalse(body.get().contains("secret"));
            assertFalse(uri.get().contains("secret"));
            assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString("writer:writer-secret".getBytes()), auth.get());
            assertTrue(java.net.URLDecoder.decode(uri.get(), java.nio.charset.StandardCharsets.UTF_8).contains("INSERT INTO jeecg_analytics.api_call"));
            assertEquals(List.of(), store.summary(1, 2, "x' OR 1=1"));
            assertTrue(body.get().contains("{api:String}"));
            assertFalse(body.get().contains("x' OR"));
            assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString("reader:reader-secret".getBytes()), auth.get());
        } finally { server.stop(0); }
    }

    @Test void configurationRejectsUnsafeTransportAndMissingCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new ClickHouseStore(properties("http://example.com:8123")));
        assertThrows(IllegalArgumentException.class, () -> new ClickHouseStore(properties("https://user:secret@example.com")));
        assertThrows(IllegalArgumentException.class, () -> new ClickHouseStore(properties("http://127.0.0.1:8123/?password=secret")));
        var p = properties("http://127.0.0.1:8123"); p.setWriterPassword("");
        assertThrows(IllegalArgumentException.class, () -> new ClickHouseStore(p));
    }

    @Test void storageFailuresAreSafeAndReadWindowIsBounded() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] error = "password=secret schema details".getBytes(); exchange.sendResponseHeaders(500, error.length);
            try(var out = exchange.getResponseBody()) { out.write(error); }
        });
        server.start();
        try {
            var store = new ClickHouseStore(properties("http://127.0.0.1:" + server.getAddress().getPort()));
            var error = assertThrows(java.io.IOException.class, () -> store.insert(List.of(ApiCallEvent.create("a", "GET", 200, 1))));
            assertFalse(error.getMessage().contains("secret"));
            assertThrows(IllegalArgumentException.class, () -> store.summary(2, 1, ""));
            assertThrows(IllegalArgumentException.class, () -> store.summary(0, 86_400_001, ""));
        } finally { server.stop(0); }
    }

    @Test void fullBatchFlushesBeforeSparseTrafficTimerAndShutdownCancelsRequests() throws Exception {
        var arrived = new java.util.concurrent.CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            arrived.countDown(); exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.start();
        var p = properties("http://127.0.0.1:" + server.getAddress().getPort());
        p.setBatchSize(2); p.setFlushSeconds(60);
        var service = new AnalyticsService(p);
        try {
            service.record("api", "GET", 200, 1); service.record("api", "GET", 200, 1);
            assertTrue(arrived.await(3, java.util.concurrent.TimeUnit.SECONDS));
        } finally { service.close(); server.stop(0); }
    }

    @Test void timeoutIncludesStalledResponseBodyAndLargeBodiesAreRejected() throws Exception {
        var blocked = new java.util.concurrent.CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write('x'); out.flush();
                try { blocked.await(4, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        var p = properties("http://127.0.0.1:" + server.getAddress().getPort()); p.setTimeoutSeconds(1);
        try (var store = new ClickHouseStore(p)) {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () ->
                    assertThrows(java.io.IOException.class, () -> store.summary(1, 2, "")));
        } finally { blocked.countDown(); server.stop(0); }

        var large = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        large.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 2_100_000);
            try(var out = exchange.getResponseBody()) { out.write(new byte[2_100_000]); }
        });
        large.start();
        try (var store = new ClickHouseStore(properties("http://127.0.0.1:" + large.getAddress().getPort()))) {
            assertThrows(java.io.IOException.class, () -> store.summary(1, 2, ""));
        } finally { large.stop(0); }
    }
}
