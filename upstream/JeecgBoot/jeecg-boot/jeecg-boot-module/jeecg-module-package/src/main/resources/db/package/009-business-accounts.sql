-- MySQL 8. Run after 001-schema.sql, before enabling business identity management.
-- Stop on SQL errors. Resolve invalid/duplicate crosswalks manually; no mappings are deleted.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_pkg_business_accounts$$
CREATE PROCEDURE migrate_pkg_business_accounts()
BEGIN
    -- All crosswalk preflight checks precede table DDL and data writes (MySQL DDL commits).
    IF EXISTS (SELECT 1 FROM pkg_user_identity
               WHERE legacy_user_id IS NULL OR legacy_user_id<=0
                  OR jeecg_user_id IS NULL OR CHAR_LENGTH(TRIM(jeecg_user_id))=0
                  OR tenant_id IS NULL OR tenant_id<=0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid package identity mapping; repair manually before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM pkg_user_identity GROUP BY legacy_user_id HAVING COUNT(*)>1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate package legacy identity; repair manually before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM pkg_user_identity GROUP BY tenant_id,jeecg_user_id HAVING COUNT(*)>1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate package tenant/user mapping; repair manually before migration';
    END IF;

    -- 001-schema.sql (AppStatisticsMapper source) defines this historical column as INT NULL.
    -- The other five business user_id columns already use BIGINT NULL. Widen statistics before
    -- accepting generated 1e12 identities; retain deployed nullability, numeric default and comment.
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                AND table_name='pkg_app_statistics' AND column_name='user_id' AND data_type='int') THEN
        SELECT CONCAT('ALTER TABLE pkg_app_statistics MODIFY COLUMN user_id BIGINT',
                      IF(column_type LIKE '%unsigned%', ' UNSIGNED', ''),
                      IF(is_nullable='YES', ' NULL', ' NOT NULL'),
                      CASE WHEN column_default IS NOT NULL THEN CONCAT(' DEFAULT ', column_default)
                           WHEN is_nullable='YES' THEN ' DEFAULT NULL' ELSE '' END,
                      ' COMMENT ', CHAR(39), REPLACE(column_comment, CHAR(39), CONCAT(CHAR(39), CHAR(39))), CHAR(39))
          INTO @pkg_statistics_user_id_ddl
          FROM information_schema.columns WHERE table_schema=DATABASE()
           AND table_name='pkg_app_statistics' AND column_name='user_id';
        PREPARE pkg_statistics_user_id_statement FROM @pkg_statistics_user_id_ddl;
        EXECUTE pkg_statistics_user_id_statement;
        DEALLOCATE PREPARE pkg_statistics_user_id_statement;
        SET @pkg_statistics_user_id_ddl=NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                   AND table_name='pkg_user_identity' AND column_name='status') THEN
        ALTER TABLE pkg_user_identity ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                   AND table_name='pkg_user_identity' AND column_name='source') THEN
        ALTER TABLE pkg_user_identity ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'MIGRATED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                   AND table_name='pkg_user_identity' AND column_name='created_at') THEN
        ALTER TABLE pkg_user_identity ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                   AND table_name='pkg_user_identity' AND column_name='updated_at') THEN
        ALTER TABLE pkg_user_identity ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema=DATABASE() AND table_name='pkg_user_identity' AND non_unique=0
                   GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='tenant_id,jeecg_user_id'
                   AND COUNT(*)=2 AND COUNT(sub_part)=0) THEN
        ALTER TABLE pkg_user_identity ADD UNIQUE KEY uk_pkg_identity_tenant_user(tenant_id,jeecg_user_id);
    END IF;

    CREATE TABLE IF NOT EXISTS pkg_business_id_reservation (
        legacy_user_id BIGINT NOT NULL,
        source VARCHAR(16) NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (legacy_user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- Upgrade the earlier reservation schema without changing existing IDs or provenance.
    -- Historical Long.MAX_VALUE must never exhaust the independent new-account sequence.
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                AND table_name='pkg_business_id_reservation' AND column_name='legacy_user_id'
                AND extra LIKE '%auto_increment%') THEN
        ALTER TABLE pkg_business_id_reservation MODIFY COLUMN legacy_user_id BIGINT NOT NULL;
    END IF;

    -- Generate a candidate here, then explicitly reserve it in the same application transaction.
    -- Existing reservations may collide with candidates; historical seeding never touches this table.
    CREATE TABLE IF NOT EXISTS pkg_business_id_allocator (
        id BIGINT NOT NULL AUTO_INCREMENT,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=1000000000000;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
                AND table_name='pkg_business_id_allocator' AND auto_increment<1000000000000) THEN
        ALTER TABLE pkg_business_id_allocator AUTO_INCREMENT=1000000000000;
    END IF;

    -- Reserve every historical positive ID, including unbound business rows in all six tables.
    -- A duplicate reservation is a no-op: never replace its original source or timestamp.
    INSERT INTO pkg_business_id_reservation(legacy_user_id,source)
    SELECT historical.legacy_user_id,'HISTORICAL' FROM (
        SELECT legacy_user_id FROM pkg_user_identity
        UNION SELECT user_id FROM pkg_app_manager WHERE user_id IS NOT NULL AND user_id>0
        UNION SELECT user_id FROM pkg_app_statistics WHERE user_id IS NOT NULL AND user_id>0
        UNION SELECT user_id FROM pkg_load_count WHERE user_id IS NOT NULL AND user_id>0
        UNION SELECT user_id FROM pkg_pwa WHERE user_id IS NOT NULL AND user_id>0
        UNION SELECT user_id FROM pkg_notification WHERE user_id IS NOT NULL AND user_id>0
        UNION SELECT user_id FROM pkg_app_event WHERE user_id IS NOT NULL AND user_id>0
    ) historical
    ON DUPLICATE KEY UPDATE legacy_user_id=pkg_business_id_reservation.legacy_user_id;

    CREATE TABLE IF NOT EXISTS pkg_business_identity_audit (
        id BIGINT NOT NULL AUTO_INCREMENT,
        operator_id VARCHAR(64) NOT NULL,
        operator_name VARCHAR(100) NOT NULL,
        target_user_id VARCHAR(64) NOT NULL,
        tenant_id INT NOT NULL,
        legacy_user_id BIGINT NOT NULL,
        action VARCHAR(16) NOT NULL,
        before_state VARCHAR(16) NOT NULL,
        after_state VARCHAR(16) NOT NULL,
        reason VARCHAR(500) NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY idx_pkg_identity_audit_target (tenant_id,target_user_id,created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
END$$
CALL migrate_pkg_business_accounts()$$
DROP PROCEDURE migrate_pkg_business_accounts$$
DELIMITER ;
