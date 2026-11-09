-- This directory must not use the host's aggregate-leaf flag.
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE sys_permission SET always_show=0 WHERE id='pkg_mgmt_root' AND url='/package-management' AND always_show=1;
COMMIT;
