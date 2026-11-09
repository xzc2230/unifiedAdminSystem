-- Fix only the package root layout; preserve child menus, labels and role grants.
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE sys_permission SET component='LAYOUT' WHERE id='pkg_mgmt_root' AND url='/package-management' AND component IN ('layouts/default/index','/layouts/default/index');
COMMIT;
