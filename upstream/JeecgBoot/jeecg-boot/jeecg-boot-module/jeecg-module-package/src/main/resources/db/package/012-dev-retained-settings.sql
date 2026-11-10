-- Retained dev storage only; timezone/SIM risk evaluation stays disabled.
SET @pkg_dev_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pkg_app_manager' AND COLUMN_NAME='system_timezone')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN system_timezone VARCHAR(512) NULL DEFAULT NULL', 'SELECT 1');
PREPARE pkg_dev_stmt FROM @pkg_dev_ddl;
EXECUTE pkg_dev_stmt;
DEALLOCATE PREPARE pkg_dev_stmt;
SET @pkg_dev_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pkg_app_manager' AND COLUMN_NAME='whether_timezone_restrict')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN whether_timezone_restrict INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE pkg_dev_stmt FROM @pkg_dev_ddl;
EXECUTE pkg_dev_stmt;
DEALLOCATE PREPARE pkg_dev_stmt;
SET @pkg_dev_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pkg_app_manager' AND COLUMN_NAME='sim_country')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN sim_country VARCHAR(512) NULL DEFAULT NULL', 'SELECT 1');
PREPARE pkg_dev_stmt FROM @pkg_dev_ddl;
EXECUTE pkg_dev_stmt;
DEALLOCATE PREPARE pkg_dev_stmt;
SET @pkg_dev_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pkg_app_manager' AND COLUMN_NAME='whether_sim_country_restrict')=0, 'ALTER TABLE pkg_app_manager ADD COLUMN whether_sim_country_restrict INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE pkg_dev_stmt FROM @pkg_dev_ddl;
EXECUTE pkg_dev_stmt;
DEALLOCATE PREPARE pkg_dev_stmt;
