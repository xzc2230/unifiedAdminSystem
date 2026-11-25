-- Keep the Play or iTunes response that caused a later delisting.
SET @fail_reason_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pkg_store_status'
      AND COLUMN_NAME = 'fail_reason'
);
SET @fail_reason_sql := IF(
    @fail_reason_exists = 0,
    'ALTER TABLE pkg_store_status ADD COLUMN fail_reason varchar(512) NULL COMMENT ''下架原因'' AFTER fail_date',
    'SELECT 1'
);
PREPARE fail_reason_stmt FROM @fail_reason_sql;
EXECUTE fail_reason_stmt;
DEALLOCATE PREPARE fail_reason_stmt;
