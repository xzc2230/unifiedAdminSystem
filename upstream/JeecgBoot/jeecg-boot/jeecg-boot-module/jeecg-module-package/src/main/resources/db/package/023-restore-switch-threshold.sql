-- 020 reused pkg_app_ef_12 for blocked providers and replaced the GAID switch threshold grant.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_app_ef_15','pkg_menu_apps','字段编辑-AID切换阈值','','','0',NULL,NULL,'2','package:apps:editField:aidGaidSwitchThreshold','1','115','0',NULL,'1','0','0','0','0','0','1','0')
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),perms=VALUES(perms),menu_type=VALUES(menu_type),hidden=0,del_flag=0,status=VALUES(status);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT MD5(CONCAT(roles.role_id, ':', perms.permission_id)), roles.role_id, perms.permission_id, NULL, NOW(), NULL
FROM (
  SELECT DISTINCT role_id FROM sys_role_permission WHERE permission_id IN ('pkg_app_ef_10', 'pkg_app_ef_11', 'pkg_app_ef_13')
) roles
CROSS JOIN (SELECT 'pkg_app_ef_15' AS permission_id) perms;
COMMIT;
