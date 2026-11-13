-- Existing pack-stat tables need the customer category column. Safe to run more than once.
SET NAMES utf8mb4;
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pkg_pack_stat' AND COLUMN_NAME = 'customer_category'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE pkg_pack_stat ADD COLUMN customer_category VARCHAR(16) NOT NULL DEFAULT ''公司'' COMMENT ''分类：CT/公司'' AFTER region_code',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
