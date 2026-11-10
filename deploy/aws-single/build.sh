#!/usr/bin/env bash
# Run as ubuntu; builds execute inside Docker on the deployment server.
set -euo pipefail
repo=${1:-/opt/src/unifiedAdminSystem}
cd "$repo"
test -z "$(git status --porcelain --untracked-files=no)"
git pull --ff-only
mkdir -p .runtime/aws-build
cat > upstream/JeecgBoot/jeecgboot-vue3/.env.production.local <<'ENV'
VITE_USE_MOCK=false
VITE_PUBLIC_PATH=/
VITE_GLOB_API_URL=/jeecgboot
VITE_GLOB_DOMAIN_URL=http://13.222.19.112/jeecgboot
VITE_GLOB_APP_OPEN_SSO=false
VITE_GLOB_APP_OPEN_QIANKUN=false
VITE_GLOB_ICONIFY_USE_TYPE=local
ENV
sudo docker run --rm --name unified-build-backend -v "$repo:/src" -v unified-maven-cache:/root/.m2 -w /src/upstream/JeecgBoot/jeecg-boot maven:3.9.13-eclipse-temurin-21-noble mvn -B -ntp -pl jeecg-module-system/jeecg-system-start -am package -DskipTests > .runtime/aws-build/backend.log 2>&1 &
backend_pid=$!
sudo docker run --rm --name unified-build-frontend -v "$repo:/src" -v unified-pnpm-cache:/pnpm/store -w /src/upstream/JeecgBoot/jeecgboot-vue3 node:24-bookworm bash -ec 'npm install -g pnpm@12.6.0; pnpm install --frozen-lockfile --ignore-scripts --store-dir /pnpm/store; pnpm build' > .runtime/aws-build/frontend.log 2>&1 &
frontend_pid=$!
backend_status=0
frontend_status=0
wait "$backend_pid" || backend_status=$?
wait "$frontend_pid" || frontend_status=$?
if test "$backend_status" != 0 || test "$frontend_status" != 0; then
    printf 'Build failed: backend=%s frontend=%s; inspect .runtime/aws-build/*.log\n' "$backend_status" "$frontend_status" >&2
    exit 1
fi
echo 'Both server builds completed successfully.'
