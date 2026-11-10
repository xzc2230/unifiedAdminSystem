#!/usr/bin/env bash
# First installation only. Run as root on the authorized, empty application host.
set -euo pipefail
umask 077
repo=${1:-/opt/src/unifiedAdminSystem}
mw=/opt/middleware
app=/opt/unifiedAdminSystem
test "$(id -u)" = 0
test -d "$repo/.git"
if test -e "$mw/application.env" || test -e "$app/application.env" || test -d "$mw/data/mysql/mysql"; then
    echo 'Existing configuration/database detected; refusing first-install initialization.' >&2
    exit 1
fi
install -d -m 700 "$mw" "$mw/secrets"
install -d -m 755 "$app" "$app/releases" "$app/data"
install -d -o 10001 -g 10001 -m 750 "$app/data/work" "$app/data/logs" "$app/data/uploads" "$app/data/webapp" "$app/data/site-router"
install -m 600 "$repo/deploy/aws-single/middleware.compose.yaml" "$mw/compose.yaml"
install -m 644 "$repo/deploy/aws-single/application.compose.yaml" "$app/compose.yaml"
install -m 644 "$repo/deploy/aws-single/nginx.conf" "$app/nginx.conf"
install -m 644 "$repo/deploy/production/middleware/redis.conf" "$mw/redis.conf"
cp -a "$repo/deploy/clickhouse/config" "$mw/clickhouse-config"
install -d -m 755 "$mw/clickhouse"
mv "$mw/clickhouse-config" "$mw/clickhouse/config"
cp -a "$repo/deploy/clickhouse/init" "$mw/clickhouse/init"
chmod -R a+rX "$mw/clickhouse"
export UNIFIED_REPO="$repo"
python3 <<'PY'
import hashlib, os, pathlib, secrets
mw = pathlib.Path('/opt/middleware')
app = pathlib.Path('/opt/unifiedAdminSystem')
def write(path, text, mode=0o600):
    path.write_text(text)
    path.chmod(mode)
passwords = {name: secrets.token_hex(32) for name in ('mysql_root_password', 'mysql_app_password', 'redis_password', 'clickhouse_admin_password', 'clickhouse_writer_password', 'clickhouse_reader_password', 'node_token')}
for name, value in passwords.items():
    write(mw / 'secrets' / name, value + '\n')
write(mw / 'redis-users.acl', 'user default on >' + passwords['redis_password'] + ' ~* &* +@all\n', 0o644)
ch = passwords['clickhouse_admin_password']
write(mw / 'clickhouse.env', '\n'.join([
    'CLICKHOUSE_ADMIN_PASSWORD=' + ch,
    'CLICKHOUSE_ADMIN_SHA256=' + hashlib.sha256(ch.encode()).hexdigest(),
    'CLICKHOUSE_WRITER_PASSWORD=' + passwords['clickhouse_writer_password'],
    'CLICKHOUSE_READER_PASSWORD=' + passwords['clickhouse_reader_password'], '']))
write(app / 'application.env', '\n'.join([
    'MIDDLEWARE_HOST=127.0.0.1',
    'JEECG_DB_PASSWORD=' + passwords['mysql_app_password'],
    'JEECG_REDIS_PASSWORD=' + passwords['redis_password'],
    'JEECG_SIGNATURE_SECRET=dd05f1c54d63749eda95f9fa6d49v442a',
    'JEECG_PUBLIC_URL=http://13.222.19.112',
    'CLICKHOUSE_ENABLED=true', 'CLICKHOUSE_ENDPOINT=http://127.0.0.1:18123',
    'CLICKHOUSE_WRITER_USER=jeecg_writer', 'CLICKHOUSE_WRITER_PASSWORD=' + passwords['clickhouse_writer_password'],
    'CLICKHOUSE_READER_USER=jeecg_reader', 'CLICKHOUSE_READER_PASSWORD=' + passwords['clickhouse_reader_password'],
    'SP_NODE_TOKEN=' + passwords['node_token'], '']))
write(app / 'site-router.env', '\n'.join([
    'SP_NODE_ID=aws-single', 'SP_NODE_TOKEN=' + passwords['node_token'],
    'SP_CONTROL_URL=http://127.0.0.1:18080/jeecg-boot',
    'SP_SNAPSHOT_FILE=/srv/jeecg/site-router/snapshots.json',
    'SP_BIND=127.0.0.1', 'SP_PORT=18090', 'SP_POLL_MS=5000',
    'SP_TIMEOUT_MS=30000', 'SP_MAX_BODY_BYTES=67108864',
    'SP_EXTERNAL_PROTO=http', 'SP_ALLOWED_UPSTREAMS=', '']))
config = (pathlib.Path(os.environ['UNIFIED_REPO']) / 'deploy/production/application/application-server.yml').read_text()
config = config.replace('siteplatform:\n', 'siteplatform:\n  node:\n    id: aws-single\n    token: ${SP_NODE_TOKEN}\n    assignments: \'\'\n', 1)
write(app / 'application-server.yml', config, 0o644)
admin_password = 'Ua!' + secrets.token_hex(16) + '9A'
write(mw / 'secrets/admin_password', admin_password + '\n')
write(mw / 'secrets/admin_salt', secrets.token_hex(4) + '\n')
write(app / 'admin-credentials.txt', 'URL: http://13.222.19.112\nUsername: admin\nPassword: ' + admin_password + '\n')
PY
cd "$mw"
docker compose config --quiet
docker compose up -d --wait --wait-timeout 300
sql() { docker compose exec -T mysql sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql -uroot "$@"' sh "$@"; }
test "$(sql -Nse 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=0x6a656563672d626f6f74')" = 0
sql < "$repo/upstream/JeecgBoot/jeecg-boot/db/jeecgboot-mysql-5.7.sql"
for migration in "$repo"/upstream/JeecgBoot/jeecg-boot/db/site-platform/*.sql; do
    sql jeecg-boot < "$migration"
done
app_password=$(cat secrets/mysql_app_password)
sql <<SQL
CREATE USER 'jeecg_app'@'%' IDENTIFIED BY '${app_password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`jeecg-boot\`.* TO 'jeecg_app'@'%';
SQL
unset app_password
cat > "$mw/secrets/AdminHash.java" <<'JAVA'
import java.nio.file.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.HexFormat;
public class AdminHash {
    public static void main(String[] args) throws Exception {
        String password = Files.readString(Path.of("/secrets/admin_password")).trim();
        String salt = Files.readString(Path.of("/secrets/admin_salt")).trim();
        var key = SecretKeyFactory.getInstance("PBEWithMD5AndDES").generateSecret(new PBEKeySpec(password.toCharArray()));
        var cipher = Cipher.getInstance("PBEWithMD5AndDES");
        cipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(salt.getBytes(java.nio.charset.StandardCharsets.UTF_8), 1000));
        System.out.print(HexFormat.of().formatHex(cipher.doFinal("admin".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
JAVA
docker run --rm -v "$mw/secrets:/secrets:ro" maven:3.9.13-eclipse-temurin-21-noble java /secrets/AdminHash.java > "$mw/secrets/admin_password.hash"
admin_hash=$(cat secrets/admin_password.hash)
admin_salt=$(cat secrets/admin_salt)
[[ $admin_hash =~ ^[a-f0-9]+$ ]]
[[ $admin_salt =~ ^[a-f0-9]{8}$ ]]
sql jeecg-boot <<SQL
UPDATE sys_user SET password='${admin_hash}', salt='${admin_salt}', status=1 WHERE username='admin';
UPDATE sys_user SET status=2 WHERE username <> 'admin';
UPDATE sys_quartz_job SET status=-1;
SQL
unset admin_hash admin_salt
docker compose exec -T clickhouse bash /opt/jeecg-init/01-schema.sh
REDISCLI_AUTH=$(cat secrets/redis_password) docker compose exec -T -e REDISCLI_AUTH redis redis-cli ping
python3 <<'PY'
import pathlib, subprocess
mw = pathlib.Path('/opt/middleware')
names = {'MYSQL_IMAGE': 'mysql', 'REDIS_IMAGE': 'redis', 'CLICKHOUSE_IMAGE': 'clickhouse'}
lines = []
for var, name in names.items():
    image_id = subprocess.check_output(['docker', 'compose', 'images', '-q', name], cwd=mw, text=True).strip()
    result = subprocess.check_output(['docker', 'image', 'inspect', image_id, '--format', '{{index .RepoDigests 0}}'], text=True).strip()
    lines.append(var + '=' + result)
(mw / '.env').write_text('\n'.join(lines) + '\n')
(mw / '.env').chmod(0o600)
PY
date -u +%FT%TZ > "$mw/.initialized"
docker compose ps
echo 'Middleware initialized; application password is in /opt/unifiedAdminSystem/admin-credentials.txt (root only).'
