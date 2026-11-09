-- app_manager: system timezone + SIM country fields and restrict switches (0 off, 1 on, same as whether_restrict)

CREATE TABLE IF NOT EXISTS pkg_app_device_name_blacklist (
                                                         id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                         device_name_keyword VARCHAR(128)    NOT NULL,
    match_mode          VARCHAR(16)     NOT NULL DEFAULT 'contains',
    case_sensitive      TINYINT(1)      NOT NULL DEFAULT 0,
    action              VARCHAR(16)     NOT NULL DEFAULT 'block',
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    priority            INT             NOT NULL DEFAULT 0,
    remark              VARCHAR(512)    NULL DEFAULT NULL,
    create_by           VARCHAR(64)     NULL DEFAULT '',
    create_time         DATETIME        NULL DEFAULT NULL,
    update_by           VARCHAR(64)     NULL DEFAULT '',
    update_time         DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_enabled_keyword (enabled, device_name_keyword(32)),
    KEY idx_priority (priority)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='app device name blacklist';

-- ASN / device brand / CPU ABI blacklist tables (app_ prefix)

CREATE TABLE IF NOT EXISTS pkg_app_asn_blacklist (
                                                 id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                 asn             VARCHAR(32)     NOT NULL,
    asn_name        VARCHAR(256)    NULL DEFAULT NULL,
    org_keyword     VARCHAR(256)    NULL DEFAULT NULL,
    match_type      VARCHAR(32)     NOT NULL DEFAULT 'asn_exact',
    action          VARCHAR(16)     NOT NULL DEFAULT 'block',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    priority        INT             NOT NULL DEFAULT 0,
    remark          VARCHAR(512)    NULL DEFAULT NULL,
    create_by       VARCHAR(64)     NULL DEFAULT '',
    create_time     DATETIME        NULL DEFAULT NULL,
    update_by       VARCHAR(64)     NULL DEFAULT '',
    update_time     DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_asn (asn),
    KEY idx_enabled_priority (enabled, priority)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='app asn blacklist';

CREATE TABLE IF NOT EXISTS pkg_app_device_brand_blacklist (
                                                          id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                          brand_keyword   VARCHAR(128)    NOT NULL,
    match_mode      VARCHAR(16)     NOT NULL DEFAULT 'contains',
    case_sensitive  TINYINT(1)      NOT NULL DEFAULT 0,
    action          VARCHAR(16)     NOT NULL DEFAULT 'block',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    priority        INT             NOT NULL DEFAULT 0,
    remark          VARCHAR(512)    NULL DEFAULT NULL,
    create_by       VARCHAR(64)     NULL DEFAULT '',
    create_time     DATETIME        NULL DEFAULT NULL,
    update_by       VARCHAR(64)     NULL DEFAULT '',
    update_time     DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_enabled_keyword (enabled, brand_keyword(32)),
    KEY idx_priority (priority)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='app device brand blacklist';

CREATE TABLE IF NOT EXISTS pkg_app_cpu_abi_blacklist (
                                                     id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                     abi_keyword     VARCHAR(128)    NOT NULL,
    match_mode      VARCHAR(16)     NOT NULL DEFAULT 'contains',
    case_sensitive  TINYINT(1)      NOT NULL DEFAULT 0,
    action          VARCHAR(16)     NOT NULL DEFAULT 'block',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    priority        INT             NOT NULL DEFAULT 0,
    remark          VARCHAR(512)    NULL DEFAULT NULL,
    create_by       VARCHAR(64)     NULL DEFAULT '',
    create_time     DATETIME        NULL DEFAULT NULL,
    update_by       VARCHAR(64)     NULL DEFAULT '',
    update_time     DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_enabled_keyword (enabled, abi_keyword(32)),
    KEY idx_priority (priority)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='app cpu abi blacklist';


SET @pkg_v4_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND column_name='gaid_aid_threshold')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN gaid_aid_threshold INT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE pkg_v4_stmt FROM @pkg_v4_ddl;
EXECUTE pkg_v4_stmt;
DEALLOCATE PREPARE pkg_v4_stmt;

SET @pkg_v4_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND column_name='aid_device_threshold')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN aid_device_threshold INT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE pkg_v4_stmt FROM @pkg_v4_ddl;
EXECUTE pkg_v4_stmt;
DEALLOCATE PREPARE pkg_v4_stmt;

SET @pkg_v4_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND column_name='aid_gaid_switch_threshold')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN aid_gaid_switch_threshold INT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE pkg_v4_stmt FROM @pkg_v4_ddl;
EXECUTE pkg_v4_stmt;
DEALLOCATE PREPARE pkg_v4_stmt;

SET @pkg_v4_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND column_name='risk_window_seconds')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN risk_window_seconds INT NOT NULL DEFAULT 86400', 'SELECT 1');
PREPARE pkg_v4_stmt FROM @pkg_v4_ddl;
EXECUTE pkg_v4_stmt;
DEALLOCATE PREPARE pkg_v4_stmt;

SET @pkg_v4_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND column_name='referrer_blacklist_json')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN referrer_blacklist_json TEXT NULL', 'SELECT 1');
PREPARE pkg_v4_stmt FROM @pkg_v4_ddl;
EXECUTE pkg_v4_stmt;
DEALLOCATE PREPARE pkg_v4_stmt;
