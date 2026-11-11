-- Keep the iOS App Store product status beside the numeric listing state. Idempotent.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_pkg_ios_product_status$$
CREATE PROCEDURE migrate_pkg_ios_product_status()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pkg_store_status' AND column_name = 'product_status') = 0 THEN
        ALTER TABLE pkg_store_status ADD COLUMN product_status VARCHAR(64) NULL AFTER status;
    END IF;
    UPDATE pkg_store_status
    SET product_status = CASE status WHEN 0 THEN '待上架' WHEN 1 THEN '已上架' WHEN 2 THEN '已下架' ELSE product_status END
    WHERE platform = 'ios' AND status IN (0, 1, 2) AND (product_status IS NULL OR product_status = '');
END$$
DELIMITER ;
CALL migrate_pkg_ios_product_status();
DROP PROCEDURE IF EXISTS migrate_pkg_ios_product_status;
