-- Unique default application code. Idempotent. Existing rows receive one code; new rows are filled by the application service.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_pkg_application_code$$
CREATE PROCEDURE migrate_pkg_application_code()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pkg_app_manager' AND column_name = 'application_code') = 0 THEN
        ALTER TABLE pkg_app_manager ADD COLUMN application_code VARCHAR(32) NULL;
    END IF;

    UPDATE pkg_app_manager
    SET application_code = CONCAT(
        CHAR(65 + (id % 26)),
        CHAR(97 + (MOD(id DIV 26, 26))),
        SUBSTRING(MD5(CONCAT('pkg-app-', id)), 1, 4)
    )
    WHERE application_code IS NULL OR application_code = '';

    IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'pkg_app_manager' AND index_name = 'uk_pkg_app_manager_application_code') = 0 THEN
        ALTER TABLE pkg_app_manager ADD UNIQUE KEY uk_pkg_app_manager_application_code (application_code);
    END IF;
END$$
DELIMITER ;
CALL migrate_pkg_application_code();
DROP PROCEDURE IF EXISTS migrate_pkg_application_code;
