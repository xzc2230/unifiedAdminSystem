-- MySQL 8 / H2 MySQL mode. InnoDB is required for transaction/CAS guarantees.
-- Deployment must use utf8mb4 and a case-sensitive collation (utf8mb4_bin).
CREATE TABLE IF NOT EXISTS attr_application (
 application_id VARCHAR(64) PRIMARY KEY, scope_id VARCHAR(32) NOT NULL, application_code VARCHAR(64) NOT NULL UNIQUE,
 owner_user_id VARCHAR(64) NOT NULL, active_version VARCHAR(64) NOT NULL, enabled INT NOT NULL DEFAULT 1, INDEX idx_attr_scope_owner(scope_id,owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS attr_app_grant (
 scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64) NOT NULL, user_id VARCHAR(64) NOT NULL, can_write INT NOT NULL DEFAULT 0,
 PRIMARY KEY(scope_id,application_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_config_version (
 scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64) NOT NULL, config_version VARCHAR(64) NOT NULL,
 schema_version VARCHAR(16) NOT NULL, payload LONGTEXT NOT NULL, checksum VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL,
 PRIMARY KEY(scope_id,application_id,config_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_admin_idempotency (
 scope_id VARCHAR(32) NOT NULL, actor_id VARCHAR(64) NOT NULL, route_digest VARCHAR(64) NOT NULL, key_digest VARCHAR(64) NOT NULL,
 request_digest VARCHAR(64) NOT NULL, response_json LONGTEXT, created_at BIGINT NOT NULL,
 PRIMARY KEY(scope_id,actor_id,route_digest,key_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_click_snapshot (
 scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64) NOT NULL, click_ref VARCHAR(100) NOT NULL, entry_key VARCHAR(100) NOT NULL,
 subject_id VARCHAR(100) NOT NULL, channel_id VARCHAR(64) NOT NULL, payload LONGTEXT NOT NULL, created_at BIGINT NOT NULL,
 PRIMARY KEY(scope_id,application_id,click_ref), UNIQUE(scope_id,application_id,entry_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_attribution_state (
 scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64) NOT NULL, click_ref VARCHAR(100) NOT NULL,
 snapshot_version VARCHAR(64) NOT NULL, evidence_id VARCHAR(100) NOT NULL, state VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL,
 PRIMARY KEY(scope_id,application_id,click_ref,snapshot_version), UNIQUE(scope_id,application_id,evidence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_subject_credential (
 credential_digest VARCHAR(64) PRIMARY KEY, scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64) NOT NULL,
 subject_id VARCHAR(100) NOT NULL, purpose VARCHAR(32) NOT NULL, expires_at BIGINT NOT NULL, revoked INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_subject_identifier (
 scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64) NOT NULL, identifier_type VARCHAR(16) NOT NULL,
 identifier_hmac VARCHAR(64) NOT NULL, subject_id VARCHAR(100) NOT NULL, key_version VARCHAR(32) NOT NULL,
 PRIMARY KEY(scope_id,application_id,identifier_type,identifier_hmac)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_resolve_token (
 token_digest VARCHAR(64) PRIMARY KEY, scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64) NOT NULL,
 subject_id VARCHAR(100) NOT NULL, channel_id VARCHAR(64) NOT NULL, click_ref VARCHAR(100) NOT NULL,
 purpose VARCHAR(32) NOT NULL, expires_at BIGINT NOT NULL, revoked INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_fallback_snapshot (
 scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64) NOT NULL, identifier_type VARCHAR(16) NOT NULL,
 identifier_hmac VARCHAR(64) NOT NULL, key_version VARCHAR(32) NOT NULL, template_version VARCHAR(64) NOT NULL,
 internal_channel_id VARCHAR(64) NOT NULL, payload LONGTEXT NOT NULL, created_at BIGINT NOT NULL,
 PRIMARY KEY(scope_id,application_id,identifier_type,identifier_hmac)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_safety (
 scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64) NOT NULL, kind VARCHAR(16) NOT NULL,
 value_digest VARCHAR(64) NOT NULL, blocked INT NOT NULL, metadata_json LONGTEXT,
 PRIMARY KEY(scope_id,application_id,kind,value_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_rate_limit (
 bucket_digest VARCHAR(64) NOT NULL, window_start BIGINT NOT NULL, request_count INT NOT NULL,
 PRIMARY KEY(bucket_digest,window_start), INDEX idx_attr_rate_window(window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS attr_audit (
 audit_id VARCHAR(64) PRIMARY KEY, scope_id VARCHAR(32) NOT NULL, application_id VARCHAR(64), actor_id VARCHAR(100) NOT NULL,
 action VARCHAR(64) NOT NULL, detail_json LONGTEXT NOT NULL, created_at BIGINT NOT NULL,
 INDEX idx_attr_audit_app(scope_id,application_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
