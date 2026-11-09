-- Stable IDs; repeatable. Match the host's canonical system-management URL, not a fixture ID.
-- Existing package menu seeds do not grant roles; this seed follows the same policy.
-- Explicitly assign this menu and its four exact permissions to the platform admin role
-- through host role management after review. No wildcard or ordinary-role grants are made.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_pkg_business_identity_menu$$
CREATE PROCEDURE migrate_pkg_business_identity_menu()
BEGIN
    DECLARE system_parent VARCHAR(64);
    IF (SELECT COUNT(*) FROM sys_permission WHERE url='/isystem' AND menu_type=0 AND del_flag=0)<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected one canonical /isystem root before business identity menu migration';
    END IF;
    SELECT id INTO system_parent FROM sys_permission WHERE url='/isystem' AND menu_type=0 AND del_flag=0;

    INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external)
    VALUES ('pkg_identity_menu',system_parent,'业务账号管理','/system/business-identity','system/businessIdentity/index',1,'BusinessIdentity',NULL,1,NULL,'1',20,0,'ant-design:user-switch-outlined',0,0,0,0,0,0,'1',0)
    ON DUPLICATE KEY UPDATE parent_id=system_parent,name=VALUES(name),url=VALUES(url),component=VALUES(component),component_name=VALUES(component_name),is_route=1,menu_type=1,perms=NULL,is_leaf=0,hidden=0,del_flag=0,status='1';

    INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external)
    VALUES ('pkg_identity_query','pkg_identity_menu','业务账号-查询','','',0,NULL,NULL,2,'package:identity:query','1',1,0,NULL,1,0,0,0,0,0,'1',0),
           ('pkg_identity_open','pkg_identity_menu','业务账号-开户','','',0,NULL,NULL,2,'package:identity:open','1',2,0,NULL,1,0,0,0,0,0,'1',0),
           ('pkg_identity_bind','pkg_identity_menu','业务账号-历史绑定','','',0,NULL,NULL,2,'package:identity:bind','1',3,0,NULL,1,0,0,0,0,0,'1',0),
           ('pkg_identity_status','pkg_identity_menu','业务账号-状态变更','','',0,NULL,NULL,2,'package:identity:status','1',4,0,NULL,1,0,0,0,0,0,'1',0)
    ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),url='',component='',is_route=0,menu_type=2,perms=VALUES(perms),is_leaf=1,hidden=0,del_flag=0,status='1';
END$$
CALL migrate_pkg_business_identity_menu()$$
DROP PROCEDURE migrate_pkg_business_identity_menu$$
DELIMITER ;
