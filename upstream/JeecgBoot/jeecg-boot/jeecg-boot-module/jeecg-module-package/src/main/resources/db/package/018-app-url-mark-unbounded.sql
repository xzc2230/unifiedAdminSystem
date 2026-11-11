-- Application link and remark have no length cap. Idempotent.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_pkg_app_url_mark$$
CREATE PROCEDURE migrate_pkg_app_url_mark()
BEGIN
    IF (SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pkg_app_manager' AND column_name = 'url') <> 'longtext' THEN
        ALTER TABLE pkg_app_manager MODIFY COLUMN url LONGTEXT NULL;
    END IF;
    IF (SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pkg_app_manager' AND column_name = 'mark') <> 'longtext' THEN
        ALTER TABLE pkg_app_manager MODIFY COLUMN mark LONGTEXT NULL;
    END IF;
END$$
DELIMITER ;
CALL migrate_pkg_app_url_mark();
DROP PROCEDURE IF EXISTS migrate_pkg_app_url_mark;
