-- Opt in existing package list pages to tab caching; do not change role grants.
-- Apply after the package menu seeds. Back up affected rows before deployment.
START TRANSACTION;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packageapps' WHERE id = 'pkg_menu_apps' AND url = '/package-management/apps' AND component = 'packagemanagement/apps/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packagestore' WHERE id = 'pkg_menu_store' AND url = '/package-management/store' AND component = 'packagemanagement/store/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packagestatistics' WHERE id = 'pkg_menu_statistics' AND url = '/package-management/statistics' AND component = 'packagemanagement/statistics/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packageeventconfigs' WHERE id = 'pkg_menu_event_configs' AND url = '/package-management/event-configs' AND component = 'packagemanagement/event-configs/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packageeventhistory' WHERE id = 'pkg_menu_event_history' AND url = '/package-management/event-history' AND component = 'packagemanagement/event-history/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packageeventerrors' WHERE id = 'pkg_menu_event_errors' AND url = '/package-management/event-errors' AND component = 'packagemanagement/event-errors/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packagepwa' WHERE id = 'pkg_menu_pwa' AND url = '/package-management/pwa' AND component = 'packagemanagement/pwa/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packagew2a' WHERE id = 'pkg_menu_w2a' AND url = '/package-management/w2a' AND component = 'packagemanagement/w2a/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packagelandingcounts' WHERE id = 'pkg_menu_landing_counts' AND url = '/package-management/landing-counts' AND component = 'packagemanagement/landing-counts/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packageappevents' WHERE id = 'pkg_menu_app_events' AND url = '/package-management/app-events' AND component = 'packagemanagement/app-events/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packagerequestlogs' WHERE id = 'pkg_menu_request_logs' AND url = '/package-management/request-logs' AND component = 'packagemanagement/request-logs/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packagenotifications' WHERE id = 'pkg_menu_notifications' AND url = '/package-management/notifications' AND component = 'packagemanagement/notifications/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packagereviews' WHERE id = 'pkg_menu_reviews' AND url = '/package-management/reviews' AND component = 'packagemanagement/reviews/index' AND del_flag = 0;
UPDATE sys_permission SET keep_alive = 1, component_name = 'Packagepackstat' WHERE id = 'pkg_menu_packstat' AND url = '/package-management/packstat' AND component = 'packagemanagement/packstat/index' AND del_flag = 0;
COMMIT;
