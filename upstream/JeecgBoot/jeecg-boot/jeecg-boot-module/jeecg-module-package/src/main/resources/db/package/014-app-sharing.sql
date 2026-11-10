-- Sharing never rewrites pkg_resource_owner or business user_id.
CREATE TABLE IF NOT EXISTS pkg_app_share (
    tenant_id INT NOT NULL,
    app_id BIGINT NOT NULL,
    grantee_user_id VARCHAR(64) NOT NULL,
    granted_by VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, app_id, grantee_user_id),
    KEY idx_pkg_app_share_grantee (tenant_id, grantee_user_id, app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Audit survives revocation and application deletion. No cascading foreign keys.
CREATE TABLE IF NOT EXISTS pkg_app_share_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id INT NOT NULL,
    app_id BIGINT NOT NULL,
    grantee_user_id VARCHAR(64) NOT NULL,
    operator_user_id VARCHAR(64) NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pkg_app_share_audit_app (tenant_id, app_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Preserve existing permission identifiers and role grants. Never grant permissions here.
UPDATE sys_permission SET name='共享' WHERE id='pkg_1_transfer';
