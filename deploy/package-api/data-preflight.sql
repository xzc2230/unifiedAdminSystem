-- Read-only checks for an imported package dataset in the selected JEECG database.
-- Run after comparing 001-schema.sql with the source production DDL and importing verified identities.
-- An empty development database returning zero issues does not validate production migration.

-- Each host user/tenant must have exactly one legacy identity.
SELECT tenant_id, jeecg_user_id, COUNT(*) AS ambiguous_legacy_ids
FROM pkg_user_identity GROUP BY tenant_id, jeecg_user_id HAVING COUNT(*) <> 1;

-- Identity and owner records require an active user, tenant and membership.
SELECT i.legacy_user_id, i.jeecg_user_id, i.tenant_id AS invalid_identity
FROM pkg_user_identity i
WHERE NOT EXISTS (
  SELECT 1 FROM sys_user_tenant m
  JOIN sys_tenant t ON t.id=m.tenant_id JOIN sys_user u ON u.id=m.user_id
  WHERE m.tenant_id=i.tenant_id
    AND m.user_id COLLATE utf8mb4_general_ci=i.jeecg_user_id COLLATE utf8mb4_general_ci
    AND m.status='1' AND t.status=1 AND t.del_flag=0 AND u.status=1 AND u.del_flag=0
    AND (t.begin_date IS NULL OR t.begin_date<=NOW()) AND (t.end_date IS NULL OR t.end_date>NOW())
);
SELECT o.resource_type, o.resource_id, o.tenant_id, o.owner_user_id AS invalid_owner
FROM pkg_resource_owner o
WHERE NOT EXISTS (
  SELECT 1 FROM pkg_user_identity i WHERE i.tenant_id=o.tenant_id AND i.jeecg_user_id=o.owner_user_id
);

-- Missing ownership denies management access, including tenant administrators.
WITH resources AS (
  SELECT 'pkg_app_manager' resource_type, id resource_id FROM pkg_app_manager
  UNION ALL SELECT 'pkg_app_statistics', id FROM pkg_app_statistics
  UNION ALL SELECT 'pkg_store_status', id FROM pkg_store_status
  UNION ALL SELECT 'pkg_event_config', id FROM pkg_event_config
  UNION ALL SELECT 'pkg_event_history', id FROM pkg_event_history
  UNION ALL SELECT 'pkg_event_error', id FROM pkg_event_error
  UNION ALL SELECT 'pkg_load_count', id FROM pkg_load_count
  UNION ALL SELECT 'pkg_pwa', id FROM pkg_pwa
  UNION ALL SELECT 'pkg_pwa_subscription', id FROM pkg_pwa_subscription
  UNION ALL SELECT 'pkg_notification', id FROM pkg_notification
  UNION ALL SELECT 'pkg_app_event', event_id FROM pkg_app_event
  UNION ALL SELECT 'pkg_review', reviews_id FROM pkg_review
)
SELECT r.resource_type, r.resource_id AS missing_owner
FROM resources r LEFT JOIN pkg_resource_owner o
  ON o.resource_type=r.resource_type AND o.resource_id=r.resource_id
WHERE o.resource_id IS NULL;

-- Global public lookup keys cannot be made tenant-local without breaking old clients.
SELECT package_code, COUNT(*) AS duplicate_packages
FROM pkg_app_manager GROUP BY package_code HAVING COUNT(*) > 1;
SELECT app_code, COUNT(*) AS duplicate_app_codes
FROM pkg_pwa GROUP BY app_code HAVING COUNT(*) > 1;

-- Preserve the source parent IDs and reconcile orphaned records before enabling writes.
SELECT 'pkg_review' resource_type, r.reviews_id resource_id, r.app_id
FROM pkg_review r LEFT JOIN pkg_pwa p ON p.id=r.app_id WHERE p.id IS NULL
UNION ALL
SELECT 'pkg_pwa_subscription', s.id, s.app_id
FROM pkg_pwa_subscription s LEFT JOIN pkg_pwa p ON p.id=s.app_id WHERE p.id IS NULL
UNION ALL
SELECT 'pkg_notification', n.id, n.app_id
FROM pkg_notification n LEFT JOIN pkg_pwa p ON p.id=n.app_id WHERE p.id IS NULL;

-- Compare this inventory and monetary total with a source snapshot at the same write boundary.
SELECT 'pkg_app_manager' table_name, COUNT(*) row_count FROM pkg_app_manager
UNION ALL SELECT 'pkg_app_statistics', COUNT(*) FROM pkg_app_statistics
UNION ALL SELECT 'pkg_store_status', COUNT(*) FROM pkg_store_status
UNION ALL SELECT 'pkg_event_config', COUNT(*) FROM pkg_event_config
UNION ALL SELECT 'pkg_event_history', COUNT(*) FROM pkg_event_history
UNION ALL SELECT 'pkg_event_error', COUNT(*) FROM pkg_event_error
UNION ALL SELECT 'pkg_load_count', COUNT(*) FROM pkg_load_count
UNION ALL SELECT 'pkg_pwa', COUNT(*) FROM pkg_pwa
UNION ALL SELECT 'pkg_pwa_subscription', COUNT(*) FROM pkg_pwa_subscription
UNION ALL SELECT 'pkg_notification', COUNT(*) FROM pkg_notification
UNION ALL SELECT 'pkg_app_event', COUNT(*) FROM pkg_app_event
UNION ALL SELECT 'pkg_review', COUNT(*) FROM pkg_review;
SELECT COALESCE(SUM(reigister_num),0) reigister_num, COALESCE(SUM(payment_num),0) payment_num,
       COALESCE(SUM(payment_total),0) payment_total FROM pkg_app_statistics;

-- Report presence only; never print private key material in verification output.
SELECT id, app_code, link_id,
       CASE WHEN public_key IS NULL OR public_key='' THEN 0 ELSE 1 END AS has_public_key,
       CASE WHEN private_key IS NULL OR private_key='' THEN 0 ELSE 1 END AS has_private_key
FROM pkg_pwa;
