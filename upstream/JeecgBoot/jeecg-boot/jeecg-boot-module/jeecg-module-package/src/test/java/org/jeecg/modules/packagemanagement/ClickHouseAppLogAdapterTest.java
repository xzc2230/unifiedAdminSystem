package org.jeecg.modules.packagemanagement;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jeecg.modules.packagemanagement.application.port.AppLogPort;
import org.jeecg.modules.packagemanagement.config.RequestLogClickHouseProperties;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.integration.ClickHouseAppLogAdapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClickHouseAppLogAdapterTest {
    @Test void disabledQueriesAndWritesFailClosedWithoutAConnection() {
        var adapter = new ClickHouseAppLogAdapter(new RequestLogClickHouseProperties());
        var failure = assertThrows(PackageFailure.class, () -> adapter.query(new AppLog(), 1, 10, List.of("com.test")));
        assertEquals(503, failure.getCode());
        assertThrows(PackageFailure.class, () -> adapter.save(log()));
        var unsafe = properties("http://example.com:8123");
        assertThrows(IllegalArgumentException.class, () -> new ClickHouseAppLogAdapter(unsafe));
        var missing = properties("http://127.0.0.1:18123");
        missing.setWriterPassword("");
        assertThrows(IllegalArgumentException.class, () -> new ClickHouseAppLogAdapter(missing));
    }

    @Test void emptyPackageScopeDoesNotQuery() throws Exception {
        var hits = new AtomicInteger();
        var server = server((exchange, body) -> { hits.incrementAndGet(); return ""; });
        try {
            var adapter = new ClickHouseAppLogAdapter(properties(server));
            assertEquals(0, adapter.query(new AppLog(), 1, 10, List.of()).total());
            assertTrue(adapter.export(new AppLog(), List.of()).isEmpty());
            assertEquals(0, adapter.clean(new AppLog(), List.of()));
            assertEquals(0, hits.get());
        } finally { server.stop(0); }
    }

    @Test void listInsertDeleteAndCleanUseTheRequestLogTableAndBoundParameters() throws Exception {
        var calls = new ArrayList<Call>();
        var server = server((exchange, body) -> {
            calls.add(new Call(exchange.getRequestURI().toString(), body, exchange.getRequestHeaders().getFirst("Authorization")));
            if (body.startsWith("INSERT")) return "";
            if (body.contains("count()")) return "{\"total\":\"2\"}\n";
            if (body.startsWith("DELETE")) return "";
            return "{\"id\":\"fixed-id\",\"package_name\":\"com.test\",\"req_path\":\"/p\",\"req_ip\":\"1.1.1.1\",\"req_params\":\"{\\\"a\\\":1}\",\"req_headers\":\"h\",\"req_method\":\"GET\",\"result_msg\":\"ok\",\"error_msg\":\"\",\"req_time\":\"2023-11-14 08:00:00.000\",\"is_mobile\":1}\n";
        });
        try {
            var adapter = new ClickHouseAppLogAdapter(properties(server));
            var stored = log();
            stored.setReqParams("{\"a\":1}");
            stored.setReqHeards("h");
            adapter.save(stored);
            var insert = calls.get(0);
            assertEquals(basic("writer", "writer-secret"), insert.auth);
            String insertSql = decode(insert.uri);
            assertTrue(insertSql.contains("INSERT INTO jeecg_analytics.app_request_log"));
            assertTrue(insertSql.contains("async_insert=1"));
            assertTrue(insertSql.contains("wait_for_async_insert=1"));
            assertFalse(insertSql.contains("api_call"));
            assertFalse(insert.uri.contains("writer-secret"));
            assertTrue(insert.body.contains("\"package_name\":\"com.test\""));
            assertTrue(insert.body.contains("\"req_headers\":\"h\""));
            assertTrue(insert.body.contains("\"req_time\":\"2023-11-14 08:00:00.000\""));
            assertFalse(insert.body.contains("secret"));

            var filter = new AppLog();
            filter.setPackageName("com' OR 1=1");
            filter.setStartReqTime(Date.from(Instant.parse("2023-11-14T00:00:00Z")));
            filter.setEndReqTime(new Date(filter.getStartReqTime().getTime() + 1000));
            var page = adapter.query(filter, 2, 10, List.of("com.test", "x' OR 1=1"));
            assertEquals(2, page.total());
            assertEquals("fixed-id", page.rows().get(0).getId());
            assertEquals("h", page.rows().get(0).getReqHeards());
            assertEquals(1, page.rows().get(0).getIsMobile());
            assertEquals(stored.getReqTime(), page.rows().get(0).getReqTime());
            var count = calls.get(1);
            var select = calls.get(2);
            assertEquals(basic("reader", "reader-secret"), count.auth);
            assertEquals(basic("reader", "reader-secret"), select.auth);
            assertTrue(select.body.contains("jeecg_analytics.app_request_log"));
            assertTrue(select.body.contains("ORDER BY req_time DESC"));
            assertTrue(select.body.contains("{packages:Array(String)}"));
            assertTrue(select.body.contains("{name:String}"));
            assertTrue(select.body.contains("{start:String}"));
            assertTrue(select.body.contains("toString(req_time, 'Asia/Shanghai')"));
            assertFalse(select.body.contains("api_call"));
            assertFalse(select.body.contains("x' OR 1=1"));
            assertFalse(select.body.contains("com' OR 1=1"));
            String query = decode(select.uri);
            assertTrue(query.contains("param_limit=10"));
            assertTrue(query.contains("param_offset=10"));
            assertTrue(query.contains("2023-11-14 08:00:00"));
            assertTrue(query.contains("com.test"));
            assertTrue(query.contains("x\\' OR 1=1") || query.contains("x\\\\' OR 1=1"));

            var found = adapter.findById(stored.getId()).orElseThrow();
            assertEquals("fixed-id", found.getId());
            assertEquals(stored.getReqTime(), found.getReqTime());
            adapter.deleteAllById(List.of(stored.getId()));
            var delete = calls.get(calls.size() - 1);
            assertEquals(basic("writer", "writer-secret"), delete.auth);
            assertTrue(delete.body.startsWith("DELETE FROM jeecg_analytics.app_request_log"));
            assertTrue(delete.body.contains("{ids:Array(String)}"));
            assertFalse(delete.body.contains(stored.getId()));

            calls.clear();
            assertEquals(2, adapter.clean(filter, List.of("com.test")));
            assertTrue(calls.get(0).body.contains("count()"));
            assertTrue(calls.get(1).body.startsWith("DELETE FROM jeecg_analytics.app_request_log"));
            assertEquals(basic("writer", "writer-secret"), calls.get(1).auth);
            assertEquals(1, adapter.export(filter, List.of("com.test")).size());
        } finally { server.stop(0); }
    }

    @Test void clientProtocolContinuesWhenTheRequestLogStoreRejectsTheWrite() {
        var service = new org.jeecg.modules.packagemanagement.application.apps.impl.ClientServiceImpl();
        AppLogPort logs = mock(AppLogPort.class);
        when(logs.save(any())).thenThrow(new PackageFailure(503, "ClickHouse request logs are not configured"));
        PackageBusinessTest.inject(service, "esAppLogRepository", logs);
        var log = log();
        assertDoesNotThrow(() -> service.log(log));
        verify(logs).save(log);
    }

    private static AppLog log() {
        var log = new AppLog();
        log.setPackageName("com.test");
        log.setReqTime(Date.from(Instant.parse("2023-11-14T00:00:00Z")));
        return log;
    }

    private static RequestLogClickHouseProperties properties(String endpoint) {
        var properties = new RequestLogClickHouseProperties();
        properties.setEnabled(true);
        properties.setEndpoint(endpoint);
        properties.setWriterUser("writer");
        properties.setWriterPassword("writer-secret");
        properties.setReaderUser("reader");
        properties.setReaderPassword("reader-secret");
        return properties;
    }

    private static RequestLogClickHouseProperties properties(HttpServer server) {
        return properties("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static String basic(String user, String password) {
        return "Basic " + java.util.Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static HttpServer server(Responder responder) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] reply = responder.reply(exchange, body).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            try (var out = exchange.getResponseBody()) { out.write(reply); }
        });
        server.start();
        return server;
    }

    private interface Responder { String reply(com.sun.net.httpserver.HttpExchange exchange, String body); }
    private record Call(String uri, String body, String auth) {}
}
