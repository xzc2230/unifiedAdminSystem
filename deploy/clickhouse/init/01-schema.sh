#!/usr/bin/env bash
# Invoked by the official entrypoint and explicitly on every setup rerun.
set -euo pipefail
umask 077
client_config=$(mktemp /dev/shm/jeecg-client.XXXXXX)
trap 'rm -f "$client_config"' EXIT
# Generated credentials are hex only. Never put passwords in CLI arguments or SQL.
[[ ${CLICKHOUSE_ADMIN_PASSWORD:-} =~ ^[a-f0-9]{64}$ ]] || exit 1
[[ ${CLICKHOUSE_WRITER_PASSWORD:-} =~ ^[a-f0-9]{64}$ ]] || exit 1
[[ ${CLICKHOUSE_READER_PASSWORD:-} =~ ^[a-f0-9]{64}$ ]] || exit 1
printf '<config><host>127.0.0.1</host><user>jeecg_bootstrap</user><password>%s</password></config>\n' "$CLICKHOUSE_ADMIN_PASSWORD" > "$client_config"
ch() { clickhouse-client --config-file "$client_config" "$@"; }
if ! ch --multiquery < /opt/jeecg-init/schema.sql >/dev/null 2>&1; then
    echo 'ClickHouse schema initialization failed; inspect server diagnostics locally.' >&2
    exit 1
fi
writer_hash=$(printf '%s' "$CLICKHOUSE_WRITER_PASSWORD" | sha256sum | cut -d' ' -f1)
reader_hash=$(printf '%s' "$CLICKHOUSE_READER_PASSWORD" | sha256sum | cut -d' ' -f1)
# ALTER resets the passwords on rerun without replacing identities or granting DDL.
# REVOKE removes direct grants left by previous manual changes.
if ! ch --multiquery >/dev/null 2>&1 <<SQL
CREATE USER IF NOT EXISTS jeecg_writer IDENTIFIED WITH sha256_hash BY '$writer_hash';
CREATE USER IF NOT EXISTS jeecg_reader IDENTIFIED WITH sha256_hash BY '$reader_hash';
ALTER USER jeecg_writer IDENTIFIED WITH sha256_hash BY '$writer_hash' SETTINGS PROFILE 'analytics_writer';
ALTER USER jeecg_reader IDENTIFIED WITH sha256_hash BY '$reader_hash' SETTINGS PROFILE 'analytics_reader';
REVOKE ALL ON *.* FROM jeecg_writer;
REVOKE ALL ON *.* FROM jeecg_reader;
GRANT INSERT ON jeecg_analytics.api_call TO jeecg_writer;
GRANT SELECT ON jeecg_analytics.api_call TO jeecg_reader;
GRANT INSERT, DELETE ON jeecg_analytics.app_request_log TO jeecg_writer;
GRANT ALTER UPDATE(_row_exists) ON jeecg_analytics.app_request_log TO jeecg_writer;
GRANT SELECT ON jeecg_analytics.app_request_log TO jeecg_reader;
SQL
then
    echo 'ClickHouse account initialization failed.' >&2
    exit 1
fi
echo 'ClickHouse schema and table-scoped accounts initialized.'
