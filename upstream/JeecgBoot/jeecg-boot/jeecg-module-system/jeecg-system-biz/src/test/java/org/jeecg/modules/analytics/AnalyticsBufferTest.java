package org.jeecg.modules.analytics;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsBufferTest {
    @Test void boundedQueueNeverCallsStorageOnRequestThread() {
        var sent = new ArrayList<ApiCallEvent>();
        var buffer = new AnalyticsBuffer(2, 1, sent::addAll);
        assertTrue(buffer.offer(ApiCallEvent.create("api", "GET", 200, 12)));
        assertTrue(buffer.offer(ApiCallEvent.create("api", "POST", 500, 20)));
        assertFalse(buffer.offer(ApiCallEvent.create("api", "GET", 200, 1)));
        assertTrue(sent.isEmpty());
        assertEquals(1L, buffer.status().get("rejected"));
        buffer.flush();
        assertEquals(1, sent.size());
        assertEquals(1, buffer.status().get("queued"));
        buffer.flush();
        assertEquals(2L, buffer.status().get("written"));
    }

    @Test void ambiguousStorageFailureIsCountedAndNotRetried() {
        var buffer = new AnalyticsBuffer(2, 2, events -> { throw new java.io.IOException("secret credential"); });
        buffer.offer(ApiCallEvent.create(null, "GET", 401, 1));
        assertDoesNotThrow(buffer::flush);
        assertEquals(1L, buffer.status().get("failedOrUnknown"));
        assertEquals(0, buffer.status().get("queued"));
        assertFalse(buffer.status().toString().contains("secret"));
    }

    @Test void eventsAreBoundedAndContainOnlyFixedMetadata() {
        var event = ApiCallEvent.create("x".repeat(200), "arbitrary-secret", 0, -1);
        assertEquals(64, event.api_id().length());
        assertEquals("OTHER", event.method());
        assertEquals(500, event.status());
        assertEquals(0, event.duration_ms());
        assertEquals(6, ApiCallEvent.class.getRecordComponents().length);
        assertNotEquals(event.event_id(), ApiCallEvent.create("api", "GET", 200, 0).event_id());
    }
}
