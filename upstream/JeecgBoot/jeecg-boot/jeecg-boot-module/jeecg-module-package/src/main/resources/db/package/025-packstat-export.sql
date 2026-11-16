-- Pack-stat commission export. Existing roles receive no automatic grants.
SET NAMES utf8mb4;
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_dev_14_export','pkg_menu_packstat','包统计-导出','','','0',NULL,NULL,'2','package:packstat:export','1','5','0',NULL,'1','0','0','0','0','0','1','0')
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),perms=VALUES(perms),menu_type=VALUES(menu_type),sort_no=VALUES(sort_no),hidden=0,del_flag=0,status=VALUES(status);
