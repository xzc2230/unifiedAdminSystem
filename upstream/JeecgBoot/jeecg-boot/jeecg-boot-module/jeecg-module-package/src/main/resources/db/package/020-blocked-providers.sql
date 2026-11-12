-- Per-app provider block. Apps that already disable the United States keep Google, Apple and Amazon.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_pkg_blocked_providers$$
CREATE PROCEDURE migrate_pkg_blocked_providers()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pkg_app_manager' AND column_name = 'blocked_providers') = 0 THEN
        ALTER TABLE pkg_app_manager ADD COLUMN blocked_providers VARCHAR(64) NULL;
    END IF;
    UPDATE pkg_app_manager
    SET blocked_providers = 'google,apple,amazon'
    WHERE whether_us = 1 AND (blocked_providers IS NULL OR blocked_providers = '');
END$$
DELIMITER ;
CALL migrate_pkg_blocked_providers();
DROP PROCEDURE IF EXISTS migrate_pkg_blocked_providers;

SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_app_ef_12','pkg_menu_apps','字段编辑-禁用运营商','','','0',NULL,NULL,'2','package:apps:editField:blockedProviders','1','112','0',NULL,'1','0','0','0','0','0','1','0')
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),perms=VALUES(perms),menu_type=VALUES(menu_type),hidden=0,del_flag=0,status=VALUES(status);
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_app_qf_12','pkg_menu_apps','字段查询-禁用运营商','','','0',NULL,NULL,'2','package:apps:queryField:blockedProviders','1','212','0',NULL,'1','0','0','0','0','0','1','0')
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),perms=VALUES(perms),menu_type=VALUES(menu_type),hidden=0,del_flag=0,status=VALUES(status);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT MD5(CONCAT(roles.role_id, ':', perms.permission_id)), roles.role_id, perms.permission_id, NULL, NOW(), NULL
FROM (
  SELECT DISTINCT role_id FROM sys_role_permission WHERE permission_id = 'pkg_app_ef_9'
) roles
CROSS JOIN (SELECT 'pkg_app_ef_12' AS permission_id) perms;
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT MD5(CONCAT(roles.role_id, ':', perms.permission_id)), roles.role_id, perms.permission_id, NULL, NOW(), NULL
FROM (
  SELECT DISTINCT role_id FROM sys_role_permission WHERE permission_id = 'pkg_app_qf_9'
) roles
CROSS JOIN (SELECT 'pkg_app_qf_12' AS permission_id) perms;
COMMIT;
