-- Apply once using a migration account against the current JEECG database.
-- Creates new tables/menu entries only. Does not grant tenant membership.
CREATE TABLE sp_site (
 id varchar(32) NOT NULL PRIMARY KEY,
 tenant_id int NOT NULL,
 name varchar(100) NOT NULL,
 environment varchar(16) NOT NULL,
 enabled tinyint(1) NOT NULL DEFAULT 1,
 created_by varchar(32) NOT NULL,
 created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_sp_site_tenant(id,tenant_id),
 KEY ix_sp_site_tenant(tenant_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE sp_site_member (
 tenant_id int NOT NULL,
 site_id varchar(32) NOT NULL,
 user_id varchar(32) NOT NULL,
 role varchar(16) NOT NULL,
 created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id,site_id,user_id),
 KEY ix_sp_member_user(tenant_id,user_id),
 CONSTRAINT fk_sp_member_site FOREIGN KEY(site_id,tenant_id) REFERENCES sp_site(id,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE sp_site_target (
 id varchar(32) NOT NULL PRIMARY KEY,
 tenant_id int NOT NULL,
 site_id varchar(32) NOT NULL,
 name varchar(100) NOT NULL,
 url varchar(2000) NOT NULL,
 enabled tinyint(1) NOT NULL DEFAULT 1,
 created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY ix_sp_target_site(site_id,tenant_id),
 CONSTRAINT fk_sp_target_site FOREIGN KEY(site_id,tenant_id) REFERENCES sp_site(id,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE sp_domain_binding (
 id varchar(32) NOT NULL PRIMARY KEY,
 tenant_id int NOT NULL,
 site_id varchar(32) NOT NULL,
 hostname varchar(253) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 verified tinyint(1) NOT NULL DEFAULT 0,
 challenge varchar(64) CHARACTER SET ascii NULL,
 challenge_expires datetime(3) NOT NULL,
 verified_at datetime(3) NULL,
 created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_sp_domain_hostname(hostname),
 KEY ix_sp_domain_site(site_id,tenant_id),
 CONSTRAINT fk_sp_domain_site FOREIGN KEY(site_id,tenant_id) REFERENCES sp_site(id,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE sp_resource_audit (
 id varchar(32) NOT NULL PRIMARY KEY,
 tenant_id int NOT NULL,
 site_id varchar(32) NOT NULL,
 actor_id varchar(32) NOT NULL,
 action varchar(40) NOT NULL,
 resource_id varchar(32) NOT NULL,
 summary varchar(500) NOT NULL,
 created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY ix_sp_audit_site(tenant_id,site_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

START TRANSACTION;
INSERT INTO sys_permission(id,parent_id,name,url,component,is_route,menu_type,sort_no,icon,is_leaf,keep_alive,hidden,del_flag,status,create_by,create_time)
VALUES ('sp_resources_menu_20261106',NULL,'站点资源','/site-platform/resources','siteplatform/resources/index',1,0,45,'ant-design:global-outlined',1,0,0,0,'1','migration',NOW());

INSERT INTO sys_permission(id,parent_id,name,is_route,menu_type,perms,perms_type,sort_no,is_leaf,hidden,del_flag,status,create_by,create_time) VALUES
('sp_resources_read_20261106','sp_resources_menu_20261106','资源查询',0,2,'siteplatform:read','1',1,1,0,0,'1','migration',NOW()),
('sp_resources_create_20261106','sp_resources_menu_20261106','创建站点',0,2,'siteplatform:create','1',2,1,0,0,'1','migration',NOW()),
('sp_resources_write_20261106','sp_resources_menu_20261106','编辑站点资源',0,2,'siteplatform:write','1',3,1,0,0,'1','migration',NOW()),
('sp_resources_manage_20261106','sp_resources_menu_20261106','管理站点成员',0,2,'siteplatform:manage','1',4,1,0,0,'1','migration',NOW());

INSERT INTO sys_role_permission(id,role_id,permission_id,data_rule_ids,operate_date,operate_ip)
SELECT REPLACE(UUID(),'-',''),r.id,p.id,NULL,NOW(),'migration'
FROM sys_role r JOIN sys_permission p ON p.id IN ('sp_resources_menu_20261106','sp_resources_read_20261106','sp_resources_create_20261106','sp_resources_write_20261106','sp_resources_manage_20261106')
WHERE r.role_code='admin';
COMMIT;
