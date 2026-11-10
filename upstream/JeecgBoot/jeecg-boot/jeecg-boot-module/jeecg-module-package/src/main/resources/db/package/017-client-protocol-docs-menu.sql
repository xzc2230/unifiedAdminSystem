-- Client protocol docs page under 应用包管理. Repeatable.
-- Roles that already have the package root, or any of its pages, also receive this page.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_menu_client_docs','pkg_mgmt_root','接口文档','/package-management/client-docs','packagemanagement/client-docs/index','1','PackageClientDocs',NULL,'1',NULL,'1','19','0',NULL,'1','0','0','0','0','0','1','0')
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),url=VALUES(url),component=VALUES(component),is_route=VALUES(is_route),component_name=VALUES(component_name),menu_type=VALUES(menu_type),perms=VALUES(perms),sort_no=VALUES(sort_no),hidden=0,del_flag=0,status=VALUES(status);
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_dev_19_query','pkg_menu_client_docs','接口文档-查询','','','0',NULL,NULL,'2','package:client-docs:query','1','1','0',NULL,'1','0','0','0','0','0','1','0')
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),perms=VALUES(perms),menu_type=VALUES(menu_type),hidden=0,del_flag=0,status=VALUES(status);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT MD5(CONCAT(roles.role_id, ':', perms.permission_id)), roles.role_id, perms.permission_id, NULL, NOW(), NULL
FROM (
  SELECT DISTINCT role_id FROM sys_role_permission
  WHERE permission_id = 'pkg_mgmt_root'
     OR permission_id IN (SELECT id FROM sys_permission WHERE parent_id = 'pkg_mgmt_root')
) roles
CROSS JOIN (
  SELECT 'pkg_menu_client_docs' AS permission_id
  UNION ALL SELECT 'pkg_dev_19_query'
) perms;
COMMIT;
