-- OpenAPI business data only. Menu permissions are in 011-dev-menu.sql.
CREATE TABLE IF NOT EXISTS pkg_app_open_api_token (
 id BIGINT NOT NULL AUTO_INCREMENT, token VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 expire_time DATETIME NULL, status TINYINT NOT NULL DEFAULT 1, remark VARCHAR(500) NULL,
 tenant_id INT NOT NULL, owner_user_id VARCHAR(64) NOT NULL,
 create_by VARCHAR(64) NULL, create_time DATETIME NULL, update_by VARCHAR(64) NULL, update_time DATETIME NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_pkg_openapi_token(token), KEY idx_pkg_openapi_owner(tenant_id,owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS pkg_app_open_api_call_log (
 id BIGINT NOT NULL AUTO_INCREMENT, api_path VARCHAR(280) NOT NULL,
 request_body MEDIUMTEXT NULL, response_body MEDIUMTEXT NULL, call_time DATETIME NOT NULL,
 token VARCHAR(128) NULL, token_id BIGINT NULL, tenant_id INT NULL, owner_user_id VARCHAR(64) NULL,
 PRIMARY KEY(id), KEY idx_pkg_openapi_log_time(call_time), KEY idx_pkg_openapi_log_owner(tenant_id,owner_user_id), KEY idx_pkg_openapi_log_token(token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
