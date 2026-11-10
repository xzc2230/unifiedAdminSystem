# AWS single-server deployment plan

**Goal:** Deploy Git-sourced frontend/backend and middleware on the authorized AWS Ubuntu server.

**Architecture:** Source is pulled into `/opt/src/unifiedAdminSystem` and built there using Java 21/Maven and Node 24/pnpm containers. Copy versioned artifacts into `/opt/unifiedAdminSystem`; run application and middleware as separate Compose projects, with middleware under `/opt/middleware`. Publish HTTP on port 80; backend and middleware ports bind only to loopback. Keep credentials outside Git.

**Tech Stack:** Docker Compose, MySQL 8.0, Redis 7.2, ClickHouse 25.8, Java 21, Node 24, Nginx.

- [x] Inspect repository, SSH connectivity, remote resources, current services, and Git revision.
- [x] Pull the specified Git repository on AWS and start frontend/backend builds.
- [x] Create `deploy/aws-single/` Compose files, Nginx configuration, and server operations scripts.
- [x] Generate server-side credentials; initialize a new database only; apply the two site-platform migrations; configure restricted application accounts.
- [x] Copy remote build artifacts into a versioned release, run application Compose, and verify Nginx configuration and backend readiness.
- [x] Verify static assets, CAPTCHA, real login, menus, authorization rejection, ClickHouse authentication, and container restart recovery from the server.
- [x] Verify public entry and browser rendering after TCP 80 is allowed in the AWS security group.
- [x] Record verified Git revision, release paths, versions, operating commands, and remaining domain/TLS requirements.

Release: `20261106T104620Z-81ee2102c884`. Five healthy containers. Database has 153 tables including 10 `sp_` tables. All 212 referenced JS/CSS assets pass. Actual admin login, authenticated menus, and backend ClickHouse aggregate query pass. Container restart/recreation retained data and recovered health. Runtime credentials are mode 0600 and root-owned.

After the user allowed TCP 80 on security group `sg-0458a840682342491`, public HTTP returned 200, CAPTCHA returned success=true, and unauthenticated user API returned 401. The browser rendered `/login?redirect=/dashboard/analysis`, including login controls and CAPTCHA image, with no captured browser errors or warnings. Public login screenshot: `.runtime/aws-deploy/public-login.png`. Interactive browser login was not repeated; actual credential/CAPTCHA login and authenticated menus were already verified through the server-side acceptance script.

Final public resource check: all 212 referenced JS/CSS assets returned valid responses, runtime API URL matched the AWS public address, and `/site-platform/resources` returned HTTP 200 through the history-route fallback.

The user has explicitly authorized deployment; execute in this chat without another approval checkpoint. Do not modify existing business databases or publish credentials.
