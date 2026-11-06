-- Additive migration. Published payloads and their resource identity remain immutable.
-- All publications lock this singleton after their own site, before reading the complete live graph.
CREATE TABLE sp_route_publish_lock (
 id tinyint NOT NULL PRIMARY KEY,
 CONSTRAINT ck_sp_publish_lock CHECK(id=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO sp_route_publish_lock(id) VALUES (1);

CREATE TABLE sp_route_release (
 id varchar(32) NOT NULL PRIMARY KEY,
 tenant_id int NOT NULL,
 site_id varchar(32) NOT NULL,
 hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload mediumtext NOT NULL,
 input_json mediumtext NOT NULL,
 resource_state mediumtext NOT NULL,
 created_by varchar(32) NOT NULL,
 source_release_id varchar(32) NULL,
 created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_sp_release_scope(id,site_id,tenant_id),
 KEY ix_sp_release_history(tenant_id,site_id,created_at,id),
 CONSTRAINT fk_sp_release_site FOREIGN KEY(site_id,tenant_id) REFERENCES sp_site(id,tenant_id),
 CONSTRAINT fk_sp_release_source FOREIGN KEY(source_release_id,site_id,tenant_id) REFERENCES sp_route_release(id,site_id,tenant_id),
 CONSTRAINT ck_sp_release_input CHECK(JSON_VALID(input_json)),
 CONSTRAINT ck_sp_release_payload CHECK(JSON_VALID(payload)),
 CONSTRAINT ck_sp_release_resources CHECK(JSON_VALID(resource_state))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE sp_route_draft (
 tenant_id int NOT NULL,
 site_id varchar(32) NOT NULL,
 revision bigint NOT NULL,
 status varchar(16) NOT NULL,
 input_json mediumtext NOT NULL,
 candidate_payload mediumtext NULL,
 candidate_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 resource_state mediumtext NULL,
 submitted_by varchar(32) NULL,
 approved_by varchar(32) NULL,
 source_release_id varchar(32) NULL,
 updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id,site_id),
 CONSTRAINT fk_sp_draft_site FOREIGN KEY(site_id,tenant_id) REFERENCES sp_site(id,tenant_id),
 CONSTRAINT fk_sp_draft_source FOREIGN KEY(source_release_id,site_id,tenant_id) REFERENCES sp_route_release(id,site_id,tenant_id),
 CONSTRAINT ck_sp_draft_revision CHECK(revision>=1),
 CONSTRAINT ck_sp_draft_status CHECK(status IN ('DRAFT','SUBMITTED','APPROVED')),
 CONSTRAINT ck_sp_draft_input CHECK(JSON_VALID(input_json)),
 CONSTRAINT ck_sp_draft_candidate CHECK(candidate_payload IS NULL OR JSON_VALID(candidate_payload))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE sp_route_live (
 tenant_id int NOT NULL,
 site_id varchar(32) NOT NULL,
 release_id varchar(32) NOT NULL,
 PRIMARY KEY(tenant_id,site_id),
 CONSTRAINT fk_sp_live_site FOREIGN KEY(site_id,tenant_id) REFERENCES sp_site(id,tenant_id),
 CONSTRAINT fk_sp_live_release FOREIGN KEY(release_id,site_id,tenant_id) REFERENCES sp_route_release(id,site_id,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE sp_route_ack (
 tenant_id int NOT NULL,
 site_id varchar(32) NOT NULL,
 node_id varchar(32) NOT NULL,
 release_id varchar(32) NOT NULL,
 hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 applied_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id,site_id,node_id,release_id),
 KEY ix_sp_ack_release(tenant_id,site_id,release_id),
 CONSTRAINT fk_sp_ack_release FOREIGN KEY(release_id,site_id,tenant_id) REFERENCES sp_route_release(id,site_id,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

START TRANSACTION;
INSERT INTO sys_permission(id,parent_id,name,is_route,menu_type,perms,perms_type,sort_no,is_leaf,hidden,del_flag,status,create_by,create_time) VALUES
('sp_routing_approve_20261106','sp_resources_menu_20261106','审批路由发布',0,2,'siteplatform:approve','1',5,1,0,0,'1','migration',NOW()),
('sp_routing_publish_20261106','sp_resources_menu_20261106','发布路由版本',0,2,'siteplatform:publish','1',6,1,0,0,'1','migration',NOW());
INSERT INTO sys_role_permission(id,role_id,permission_id,data_rule_ids,operate_date,operate_ip)
SELECT REPLACE(UUID(),'-',''),r.id,p.id,NULL,NOW(),'migration'
FROM sys_role r JOIN sys_permission p ON p.id IN ('sp_routing_approve_20261106','sp_routing_publish_20261106')
WHERE r.role_code='admin';
COMMIT;
