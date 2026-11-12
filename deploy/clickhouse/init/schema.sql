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
CREATE TABLE IF NOT EXISTS jeecg_analytics.app_request_log
(
    id String,
    package_name String,
    req_path String,
    req_host String,
    req_ip String,
    req_params String,
    req_headers String,
    req_method LowCardinality(String),
    result_msg String,
    error_msg String,
    req_time DateTime64(3, 'Asia/Shanghai'),
    is_mobile UInt8
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(req_time)
ORDER BY (package_name, req_time, id)
TTL req_time + INTERVAL 30 DAY;
ALTER TABLE jeecg_analytics.app_request_log ADD COLUMN IF NOT EXISTS req_host String AFTER req_path;
