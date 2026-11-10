-- Store platform columns and composite unique keys. Idempotent; stop on duplicate package rows.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_pkg_store_platform$$
CREATE PROCEDURE migrate_pkg_store_platform()
BEGIN
    SET @pkg_platform_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND column_name='platform')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN platform VARCHAR(16) NULL DEFAULT ''android''', 'SELECT 1');
    PREPARE pkg_platform_stmt FROM @pkg_platform_ddl;
    EXECUTE pkg_platform_stmt;
    DEALLOCATE PREPARE pkg_platform_stmt;

    SET @pkg_platform_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND column_name='apple_app_id')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN apple_app_id VARCHAR(32) NULL', 'SELECT 1');
    PREPARE pkg_platform_stmt FROM @pkg_platform_ddl;
    EXECUTE pkg_platform_stmt;
    DEALLOCATE PREPARE pkg_platform_stmt;

    SET @pkg_platform_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND column_name='store_region')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN store_region VARCHAR(8) NULL', 'SELECT 1');
    PREPARE pkg_platform_stmt FROM @pkg_platform_ddl;
    EXECUTE pkg_platform_stmt;
    DEALLOCATE PREPARE pkg_platform_stmt;

    SET @pkg_platform_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_store_status' AND column_name='platform')=0, 'ALTER TABLE pkg_store_status ADD COLUMN platform VARCHAR(16) NULL DEFAULT ''android''', 'SELECT 1');
    PREPARE pkg_platform_stmt FROM @pkg_platform_ddl;
    EXECUTE pkg_platform_stmt;
    DEALLOCATE PREPARE pkg_platform_stmt;

    SET @pkg_platform_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_store_status' AND column_name='apple_app_id')=0, 'ALTER TABLE pkg_store_status ADD COLUMN apple_app_id VARCHAR(32) NULL', 'SELECT 1');
    PREPARE pkg_platform_stmt FROM @pkg_platform_ddl;
    EXECUTE pkg_platform_stmt;
    DEALLOCATE PREPARE pkg_platform_stmt;

    SET @pkg_platform_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_store_status' AND column_name='store_region')=0, 'ALTER TABLE pkg_store_status ADD COLUMN store_region VARCHAR(8) NULL', 'SELECT 1');
    PREPARE pkg_platform_stmt FROM @pkg_platform_ddl;
    EXECUTE pkg_platform_stmt;
    DEALLOCATE PREPARE pkg_platform_stmt;

    IF EXISTS (
        SELECT 1 FROM pkg_app_manager
        WHERE package_code IS NOT NULL AND CHAR_LENGTH(TRIM(package_code))>0
        GROUP BY package_code HAVING COUNT(*)>1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate package_code in pkg_app_manager; resolve manually before store platform migration';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pkg_store_status
        WHERE package_code IS NOT NULL AND CHAR_LENGTH(TRIM(package_code))>0
        GROUP BY package_code HAVING COUNT(*)>1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate package_code in pkg_store_status; resolve manually before store platform migration';
    END IF;

    UPDATE pkg_app_manager SET platform='android' WHERE platform IS NULL OR CHAR_LENGTH(TRIM(platform))=0;
    UPDATE pkg_store_status SET platform='android' WHERE platform IS NULL OR CHAR_LENGTH(TRIM(platform))=0;

    IF EXISTS (SELECT 1 FROM pkg_app_manager
               WHERE package_code IS NOT NULL AND CHAR_LENGTH(TRIM(package_code))>0
               GROUP BY platform, package_code HAVING COUNT(*)>1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate platform/package_code in pkg_app_manager; resolve manually before store platform migration';
    END IF;

    IF EXISTS (SELECT 1 FROM pkg_store_status
               WHERE package_code IS NOT NULL AND CHAR_LENGTH(TRIM(package_code))>0
               GROUP BY platform, package_code HAVING COUNT(*)>1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate platform/package_code in pkg_store_status; resolve manually before store platform migration';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND index_name='uk_pkg_app_manager_package_code') THEN
        ALTER TABLE pkg_app_manager DROP INDEX uk_pkg_app_manager_package_code;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema=DATABASE() AND table_name='pkg_app_manager' AND index_name='uk_pkg_app_manager_platform_package') THEN
        ALTER TABLE pkg_app_manager ADD UNIQUE KEY uk_pkg_app_manager_platform_package (platform, package_code);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema=DATABASE() AND table_name='pkg_store_status' AND index_name='uk_pkg_store_platform_package') THEN
        ALTER TABLE pkg_store_status ADD UNIQUE KEY uk_pkg_store_platform_package (platform, package_code);
    END IF;
END$$
DELIMITER ;
CALL migrate_pkg_store_platform();
DROP PROCEDURE migrate_pkg_store_platform;
