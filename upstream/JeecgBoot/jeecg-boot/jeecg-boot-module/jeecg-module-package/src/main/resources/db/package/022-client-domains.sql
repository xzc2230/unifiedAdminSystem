-- Client domain onboarding. Does not grant roles. The Quartz row stays stopped until an administrator starts it.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS pkg_client_domain (
  id BIGINT NOT NULL AUTO_INCREMENT,
  apex VARCHAR(253) NOT NULL,
  status VARCHAR(16) NOT NULL,
  nameservers VARCHAR(255) NULL,
  zone_id VARCHAR(64) NULL,
  error_message VARCHAR(500) NULL,
  expires_at DATETIME NULL,
  create_time DATETIME NULL,
  update_time DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pkg_client_domain_apex (apex)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

START TRANSACTION;
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_menu_domains','pkg_mgmt_root','域名管理','/package-management/domains','packagemanagement/domains/index',1,'PackageDomains',NULL,1,NULL,'1',20,0,NULL,0,1,0,0,0,0,'1',0)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),url=VALUES(url),component=VALUES(component),is_route=VALUES(is_route),component_name=VALUES(component_name),menu_type=VALUES(menu_type),perms=VALUES(perms),sort_no=VALUES(sort_no),keep_alive=VALUES(keep_alive),hidden=0,del_flag=0,status=VALUES(status);
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_domain_query','pkg_menu_domains','域名管理-查询','','',0,NULL,NULL,2,'package:domains:query','1',1,0,NULL,1,0,0,0,0,0,'1',0)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),perms=VALUES(perms),menu_type=VALUES(menu_type),hidden=0,del_flag=0,status=VALUES(status);
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_domain_add','pkg_menu_domains','域名管理-新增','','',0,NULL,NULL,2,'package:domains:add','1',2,0,NULL,1,0,0,0,0,0,'1',0)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),perms=VALUES(perms),menu_type=VALUES(menu_type),hidden=0,del_flag=0,status=VALUES(status);
INSERT INTO sys_permission (id,parent_id,name,url,component,is_route,component_name,redirect,menu_type,perms,perms_type,sort_no,always_show,icon,is_leaf,keep_alive,hidden,hide_tab,del_flag,rule_flag,status,internal_or_external) VALUES ('pkg_domain_remove','pkg_menu_domains','域名管理-删除','','',0,NULL,NULL,2,'package:domains:remove','1',3,0,NULL,1,0,0,0,0,0,'1',0)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),perms=VALUES(perms),menu_type=VALUES(menu_type),hidden=0,del_flag=0,status=VALUES(status);
INSERT INTO sys_quartz_job (id,create_by,create_time,del_flag,job_class_name,cron_expression,parameter,description,status)
SELECT 'pkg_job_domain','admin',NOW(),0,'org.jeecg.modules.packagemanagement.job.PackageDomainJob','0 0/5 * * * ?',NULL,'检测待 NS 的客户端域名，就绪后写入 nginx',-1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_quartz_job WHERE job_class_name = 'org.jeecg.modules.packagemanagement.job.PackageDomainJob' AND del_flag = 0
);
COMMIT;
