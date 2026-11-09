-- Independent single-page menu; role grants are explicit and preserved on re-install.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO sys_permission
  (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external)
VALUES
  ('attr_menu_root',NULL,'归因管理','/attribution','attribution/index',1,'AttributionManagement',NULL,1,'attribution:application:list','1',81,0,'ant-design:branches-outlined',1,0,0,0,0,0,'1',0),
  ('attr_perm_list','attr_menu_root','归因应用查询','','',0,NULL,NULL,2,'attribution:application:list','1',1,0,NULL,1,0,0,0,0,0,'1',0),
  ('attr_perm_add','attr_menu_root','归因应用新增','','',0,NULL,NULL,2,'attribution:application:add','1',2,0,NULL,1,0,0,0,0,0,'1',0),
  ('attr_perm_edit','attr_menu_root','归因应用编辑','','',0,NULL,NULL,2,'attribution:application:edit','1',3,0,NULL,1,0,0,0,0,0,'1',0),
  ('attr_perm_diagnostics','attr_menu_root','归因脱敏诊断','','',0,NULL,NULL,2,'attribution:diagnostics:view','1',4,0,NULL,1,0,0,0,0,0,'1',0),
  ('attr_perm_security','attr_menu_root','归因安全管理','','',0,NULL,NULL,2,'attribution:security:manage','1',5,0,NULL,1,0,0,0,0,0,'1',0),
  ('attr_perm_integration','attr_menu_root','归因接入管理','','',0,NULL,NULL,2,'attribution:integration:manage','1',6,0,NULL,1,0,0,0,0,0,'1',0)
ON DUPLICATE KEY UPDATE
  name=VALUES(name),url=VALUES(url),component=VALUES(component),parent_id=VALUES(parent_id),
  perms=VALUES(perms),menu_type=VALUES(menu_type),component_name=VALUES(component_name),
  always_show=VALUES(always_show),is_leaf=VALUES(is_leaf),is_route=VALUES(is_route),
  hidden=VALUES(hidden),del_flag=VALUES(del_flag),status=VALUES(status);
COMMIT;
