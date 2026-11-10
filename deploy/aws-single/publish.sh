#!/usr/bin/env bash
# Copy artifacts built on this server and publish one versioned release.
set -euo pipefail
umask 022
repo=${1:-/opt/src/unifiedAdminSystem}
app=/opt/unifiedAdminSystem
test "$(id -u)" = 0
test -f "$app/application.env"
revision=$(git -C "$repo" rev-parse HEAD)
release="$(date -u +%Y%m%dT%H%M%SZ)-${revision:0:12}"
dest="$app/releases/$release"
jar="$repo/upstream/JeecgBoot/jeecg-boot/jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.5.jar"
frontend="$repo/upstream/JeecgBoot/jeecgboot-vue3/dist"
test -f "$jar"
test -f "$frontend/index.html"
test -f "$frontend/_app.config.js"
install -d -m 755 "$dest/backend" "$dest/frontend"
install -m 644 "$jar" "$dest/backend/jeecg.jar"
cp -a "$frontend/." "$dest/frontend/"
# Upstream postBuild reads .env.production but ignores .env.production.local.
# This file is explicitly intended as the post-build deployment configuration.
python3 - "$dest/frontend/_app.config.js" <<'PY'
import json, pathlib, re, sys
path = pathlib.Path(sys.argv[1])
text = path.read_text()
match = re.search(r'^(window\.[A-Za-z_\d]+)=(\{.*?\});', text)
if not match:
    raise SystemExit('Unrecognized frontend runtime configuration format')
config = json.loads(match.group(2))
config.update(VITE_GLOB_API_URL='/jeecgboot', VITE_GLOB_DOMAIN_URL='/jeecgboot', VITE_GLOB_APP_OPEN_SSO='false', VITE_GLOB_APP_OPEN_QIANKUN='false')
text = text[:match.start(2)] + json.dumps(config, ensure_ascii=False, separators=(',', ':')) + text[match.end(2):]
path.write_text(text)
assert 'http://127.0.0.1:8080' not in text
assert config['VITE_GLOB_DOMAIN_URL'] == '/jeecgboot'
print('Validated production frontend runtime URL and standalone mode.')
PY
cp -a "$repo/services/site-router" "$dest/site-router"
chmod -R a+rX "$dest"
printf '%s\n' "$revision" > "$dest/GIT_REVISION"
date -u +%FT%TZ > "$dest/BUILT_AT"
(cd "$dest" && find backend frontend site-router -type f -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS && sha256sum -c SHA256SUMS > /dev/null)
cd "$app"
if test -L current; then
    readlink current > PREVIOUS_RELEASE
fi
ln -s "releases/$release" current.next
mv -Tf current.next current
docker compose config --quiet
docker compose pull
docker compose run --rm --no-deps frontend nginx -t
docker compose up -d --force-recreate --wait --wait-timeout 360
python3 <<'PY'
import pathlib, subprocess
app = pathlib.Path('/opt/unifiedAdminSystem')
lines = []
for var, service in {'JAVA_IMAGE': 'backend', 'NGINX_IMAGE': 'frontend'}.items():
    image_id = subprocess.check_output(['docker', 'compose', 'images', '-q', service], cwd=app, text=True).strip()
    digest = subprocess.check_output(['docker', 'image', 'inspect', image_id, '--format', '{{index .RepoDigests 0}}'], text=True).strip()
    lines.append(var + '=' + digest)
(app / '.env').write_text('\n'.join(lines) + '\n')
(app / '.env').chmod(0o600)
PY
docker compose ps
printf 'Published release: %s\n' "$release"
