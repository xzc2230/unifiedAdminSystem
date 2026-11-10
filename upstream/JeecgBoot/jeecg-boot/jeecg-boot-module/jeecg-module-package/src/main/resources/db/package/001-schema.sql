-- DEVELOPMENT SCHEMA ONLY. INFERRED from source Java fields and MyBatis XML; source business DDL was unavailable.
-- Verify actual production SQL types, lengths, defaults, constraints, indexes and historical values before migration.
-- Existing numeric IDs and user_id values are retained; this script does not convert production data.
-- MySQL 8 syntax. No foreign keys are inferred because source DDL and historical consistency are unknown.

-- Source: PackServer/src/main/resources/mybatis/system/AppManagerMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_app_manager` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `app_name` VARCHAR(255) NULL,
  `areas` TEXT NULL,
  `author` VARCHAR(255) NULL,
  `create_date` DATETIME NULL,
  `mark` VARCHAR(255) NULL,
  `package_code` VARCHAR(255) NULL,
  `application_code` VARCHAR(32) NULL,
  `platform` VARCHAR(16) NULL DEFAULT 'android',
  `apple_app_id` VARCHAR(32) NULL,
  `store_region` VARCHAR(8) NULL,
  `status` INT NULL,
  `update_date` DATETIME NULL,
  `url` TEXT NULL,
  `user_id` BIGINT NULL,
  `user_name` VARCHAR(255) NULL,
  `whether_restrict` INT NULL,
  `whether_us` INT NULL,
  `whether_use` INT NULL,
  gaid_aid_threshold INT NOT NULL DEFAULT 1,
  aid_device_threshold INT NOT NULL DEFAULT 1,
  aid_gaid_switch_threshold INT NOT NULL DEFAULT 1,
  risk_window_seconds INT NOT NULL DEFAULT 86400,
  referrer_blacklist_json TEXT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pkg_app_manager_platform_package` (`platform`, `package_code`),
  UNIQUE KEY `uk_pkg_app_manager_application_code` (`application_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/AppStatisticsMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_app_statistics` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `package_code` VARCHAR(255) NULL,
  `reigister_num` BIGINT NULL,
  `payment_num` BIGINT NULL,
  `payment_total` DECIMAL(18,2) NULL,
  `url` TEXT NULL,
  `sub_areas` VARCHAR(255) NULL,
  `sub_url` VARCHAR(255) NULL,
  `create_time` DATETIME NULL,
  `update_time` DATETIME NULL,
  `user_id` INT NULL,
  `status` INT NULL,
  `shunt` INT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/StoreStatusMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_store_status` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `package_code` VARCHAR(255) NULL,
  `platform` VARCHAR(16) NULL DEFAULT 'android',
  `apple_app_id` VARCHAR(32) NULL,
  `store_region` VARCHAR(8) NULL,
  `status` INT NULL,
  `store_url` TEXT NULL,
  `create_date` DATETIME NULL,
  `success_date` DATETIME NULL,
  `fail_date` DATETIME NULL,
  `success_email` VARCHAR(255) NULL,
  `fail_email` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pkg_store_platform_package` (`platform`, `package_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/EventConfigMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_event_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `package_id` VARCHAR(255) NULL,
  `package_token` TEXT NULL,
  `package_pixel` VARCHAR(255) NULL,
  `app_name` VARCHAR(255) NULL,
  `url` TEXT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/EvnetHistoryMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_event_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_name` VARCHAR(255) NULL,
  `event_time` VARCHAR(255) NULL,
  `event_source` VARCHAR(255) NULL,
  `media_source` VARCHAR(255) NULL,
  `campaign` VARCHAR(255) NULL,
  `af_sub1` VARCHAR(255) NULL,
  `af_sub2` VARCHAR(255) NULL,
  `af_sub3` VARCHAR(255) NULL,
  `region` VARCHAR(255) NULL,
  `country_code` VARCHAR(255) NULL,
  `state` VARCHAR(255) NULL,
  `city` VARCHAR(255) NULL,
  `postal_code` VARCHAR(255) NULL,
  `dma` VARCHAR(255) NULL,
  `ip` VARCHAR(255) NULL,
  `wifi` VARCHAR(255) NULL,
  `operator` VARCHAR(255) NULL,
  `language` VARCHAR(255) NULL,
  `appsflyer_id` VARCHAR(255) NULL,
  `platform` VARCHAR(255) NULL,
  `os_version` VARCHAR(255) NULL,
  `app_version` VARCHAR(255) NULL,
  `sdk_version` VARCHAR(255) NULL,
  `app_id` VARCHAR(255) NULL,
  `app_name` VARCHAR(255) NULL,
  `bundle_id` VARCHAR(255) NULL,
  `is_retargeting` VARCHAR(255) NULL,
  `user_agent` VARCHAR(255) NULL,
  `http_referrer` TEXT NULL,
  `original_url` TEXT NULL,
  `device_download_time` VARCHAR(255) NULL,
  `conversion_type` VARCHAR(255) NULL,
  `device_model` VARCHAR(255) NULL,
  `json_str` LONGTEXT NULL,
  `path` VARCHAR(255) NULL,
  `status` VARCHAR(255) NULL,
  `package_name` VARCHAR(255) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/EventErrorMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_event_error` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `error_package` VARCHAR(255) NULL,
  `error_message` VARCHAR(255) NULL,
  `error_content` LONGTEXT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/LoadCountMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_load_count` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `hostName` VARCHAR(255) NULL,
  `time` DATETIME NULL,
  `access` BIGINT NULL,
  `download` BIGINT NULL,
  `user_id` BIGINT NULL,
  `app_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/AppPwaMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_pwa` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `app_name` VARCHAR(255) NULL,
  `app_icon` TEXT NULL,
  `app_advertising` LONGTEXT NULL,
  `app_description` TEXT NULL,
  `app_language` VARCHAR(255) NULL,
  `app_host` VARCHAR(255) NULL,
  `app_url` TEXT NULL,
  `app_event` VARCHAR(255) NULL,
  `app_event_conf` LONGTEXT NULL,
  `user_id` BIGINT NULL,
  `app_create_time` DATETIME NULL,
  `app_change_time` DATETIME NULL,
  `app_sub_host` VARCHAR(255) NULL,
  `app_sub` VARCHAR(255) NULL,
  `link_id` VARCHAR(255) NULL,
  `public_key` TEXT NULL,
  `private_key` TEXT NULL,
  `download_url` TEXT NULL,
  `app_type` VARCHAR(255) NULL,
  `app_code` VARCHAR(255) NULL,
  `download_count` INT NULL,
  `score` VARCHAR(255) NULL,
  `reviews` INT NULL,
  `tags` TEXT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pkg_pwa_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/AppPwaSubMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_pwa_subscription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `host` VARCHAR(255) NULL,
  `endpoint` TEXT NULL,
  `auth` VARCHAR(255) NULL,
  `p256dh` VARCHAR(255) NULL,
  `app_id` BIGINT NULL,
  `create_time` DATETIME NULL,
  `send_time` DATETIME NULL,
  `send_status` INT NULL,
  `send_err` VARCHAR(255) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/AppNotifyMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `app_id` BIGINT NULL,
  `user_id` BIGINT NULL,
  `app_name` VARCHAR(255) NULL,
  `app_icon` TEXT NULL,
  `app_host` VARCHAR(255) NULL,
  `notify_title` VARCHAR(255) NULL,
  `notify_message` LONGTEXT NULL,
  `notify_icon` TEXT NULL,
  `create_time` DATETIME NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/AppEventRecordMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_app_event` (
  `event_id` BIGINT NOT NULL AUTO_INCREMENT,
  `app_id` BIGINT NULL,
  `host_name` VARCHAR(255) NULL,
  `event_type` BIGINT NULL,
  `event_type_name` VARCHAR(255) NULL,
  `device_no` VARCHAR(255) NULL,
  `ip` VARCHAR(255) NULL,
  `create_time` DATETIME NULL,
  `user_id` BIGINT NULL,
  PRIMARY KEY (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: PackServer/src/main/resources/mybatis/system/ReviewsRecordMapper.xml
CREATE TABLE IF NOT EXISTS `pkg_review` (
  `reviews_id` BIGINT NOT NULL AUTO_INCREMENT,
  `app_id` BIGINT NULL,
  `head_portrait_url` TEXT NULL,
  `name` VARCHAR(255) NULL,
  `content` LONGTEXT NULL,
  `score` BIGINT NULL,
  `create_time` DATETIME NULL,
  PRIMARY KEY (`reviews_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Explicit identity crosswalk. Populate from verified legacy and JEECG identities before enabling writes.
CREATE TABLE IF NOT EXISTS `pkg_user_identity` (
  `legacy_user_id` BIGINT NOT NULL,
  `jeecg_user_id` VARCHAR(64) NOT NULL,
  `tenant_id` INT NOT NULL,
  PRIMARY KEY (`legacy_user_id`),
  KEY `idx_pkg_user_identity_jeecg` (`jeecg_user_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Explicit resource ownership for records that do not carry their own user_id.
-- resource_type uses the pkg_* table name; resource_id is the original numeric row ID.
CREATE TABLE IF NOT EXISTS `pkg_resource_owner` (
  `resource_type` VARCHAR(64) NOT NULL,
  `resource_id` BIGINT NOT NULL,
  `tenant_id` INT NOT NULL,
  `owner_user_id` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`resource_type`, `resource_id`),
  KEY `idx_pkg_resource_owner_actor` (`owner_user_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
