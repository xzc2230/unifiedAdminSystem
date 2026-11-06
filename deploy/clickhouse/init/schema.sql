CREATE DATABASE IF NOT EXISTS jeecg_analytics;
CREATE TABLE IF NOT EXISTS jeecg_analytics.api_call
(
    event_id UUID,
    event_time DateTime64(3, 'UTC'),
    api_id String,
    method LowCardinality(String),
    status UInt16,
    duration_ms UInt32
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_time)
ORDER BY (event_time, api_id, event_id)
TTL event_time + INTERVAL 7 DAY;
