package org.jeecg.modules.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClickHouseIntegrationTest {
    @Test void realServerAcceptsBatchReturnsAggregatesAndEnforcesSeparatedGrants() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("JEECG_CLICKHOUSE_TEST")), "Opt-in real ClickHouse test");
        var p = new ClickHouseProperties(); p.setEnabled(true);
        p.setEndpoint(System.getenv("CLICKHOUSE_ENDPOINT"));
        p.setWriterUser(System.getenv("CLICKHOUSE_WRITER_USER")); p.setWriterPassword(System.getenv("CLICKHOUSE_WRITER_PASSWORD"));
        p.setReaderUser(System.getenv("CLICKHOUSE_READER_USER")); p.setReaderPassword(System.getenv("CLICKHOUSE_READER_PASSWORD"));
        String api = "integration_" + java.util.UUID.randomUUID().toString().replace("-", "");
        long start = System.currentTimeMillis() - 1000;
        try (var store = new ClickHouseStore(p)) {
            store.insert(List.of(ApiCallEvent.create(api, "GET", 200, 17), ApiCallEvent.create(api, "GET", 503, 29)));
            var rows = store.summary(start, System.currentTimeMillis() + 1000, api);
            assertEquals(1, rows.size());
            assertEquals("2", rows.get(0).get("calls").toString());
            assertEquals("1", rows.get(0).get("server_errors").toString());
        }
        var client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
        assertNotEquals(200, query(client, p.getEndpoint(), p.getWriterUser(), p.getWriterPassword(), "SELECT * FROM jeecg_analytics.api_call LIMIT 1"));
        assertNotEquals(200, query(client, p.getEndpoint(), p.getReaderUser(), p.getReaderPassword(),
                "INSERT INTO jeecg_analytics.api_call VALUES (generateUUIDv4(),now64(3),'__denied_probe__','GET',200,0)"));
        assertNotEquals(200, query(client, p.getEndpoint(), "default", "", "SELECT 1"));
    }

    private static int query(HttpClient client, String endpoint, String user, String password, String sql) throws Exception {
        String basic = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(java.time.Duration.ofSeconds(5))
                .header("Authorization", "Basic " + basic).POST(HttpRequest.BodyPublishers.ofString(sql)).build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
