-- Amazon is no longer a selectable carrier. Remove it from lists already saved by 020.
UPDATE pkg_app_manager
SET blocked_providers = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', blocked_providers, ','), ',amazon,', ','))
WHERE blocked_providers LIKE '%amazon%';
