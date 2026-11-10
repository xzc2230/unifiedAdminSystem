#!/usr/bin/env python3
"""Run on the AWS server as root; report statuses without printing credentials."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request

APP = Path('/opt/unifiedAdminSystem')
MW = Path('/opt/middleware')
BASE = 'http://127.0.0.1'


def request(path, data=None, headers=None, base=BASE):
    headers = dict(headers or {})
    if data is not None:
        data = json.dumps(data).encode()
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(base + path, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        return error.code, error.read(), error.headers


def check(label, condition):
    if not condition:
        raise AssertionError(label)
    print('PASS ' + label, flush=True)


status, body, headers = request('/')
check('frontend index', status == 200 and b'<html' in body)
html = body.decode()
assets = re.findall(r'(?:src|href)="(/[^"?]+\.(?:js|css))(?:\?[^\"]*)?"', html)
check('frontend asset references exist', bool(assets))
for asset in assets:
    status, body, headers = request(asset)
    if status != 200 or b'<html' in body[:100].lower():
        raise AssertionError('Missing or invalid asset: ' + asset)
check(str(len(assets)) + ' referenced JS/CSS assets', True)
status, body, headers = request('/_app.config.js')
check('production runtime URL', status == 200 and (b'"VITE_GLOB_DOMAIN_URL":"/jeecgboot"' in body or b'http://13.222.19.112/jeecgboot' in body) and b'127.0.0.1:8080' not in body)
status, body, headers = request('/site-platform/resources')
check('deep link fallback', status == 200 and b'<html' in body)
for path, expected in [('/missing-deploy-asset.js', 404), ('/jeecgboot/sys/user/list', 401), ('/jeecgboot/sys/user/register', 403), ('/jeecgboot/site-platform/node/snapshots', 403), ('/jeecgboot/jmreport/list', 403), ('/jeecgboot/doc.html', 403), ('/jeecg-boot/sys/user/list', 404)]:
    status, body, headers = request(path)
    check(path + ' rejects with ' + str(expected), status == expected)

# Exercise the actual generated CAPTCHA and normal login API. Read the answer
# from this deployment's own Redis, without weakening CAPTCHA in configuration.
key = 'deployment-verification-' + str(time.time_ns())
status, body, headers = request('/jeecgboot/sys/randomImage/' + key)
captcha = json.loads(body)
check('CAPTCHA generation', status == 200 and captcha.get('success') and captcha.get('result', '').startswith('data:image/'))
env = dict(line.split('=', 1) for line in (APP / 'application.env').read_text().splitlines() if '=' in line)
prefix = hashlib.md5((key + env['JEECG_SIGNATURE_SECRET']).encode()).hexdigest()
process_env = dict(os.environ, REDISCLI_AUTH=env['JEECG_REDIS_PASSWORD'])


def redis(*args):
    return subprocess.check_output(['docker', 'compose', 'exec', '-T', '-e', 'REDISCLI_AUTH', 'redis', 'redis-cli', '--raw', *args], cwd=MW, env=process_env, text=True).strip()


keys = redis('KEYS', prefix + '*').splitlines()
check('generated CAPTCHA in Redis', len(keys) == 1)
answer = redis('GET', keys[0])
if answer.startswith('"'):
    answer = json.loads(answer)
credentials = dict(line.split(': ', 1) for line in (APP / 'admin-credentials.txt').read_text().splitlines())
status, body, headers = request('/jeecgboot/sys/login', {'username': credentials['Username'], 'password': credentials['Password'], 'captcha': answer, 'checkKey': key})
login = json.loads(body)
if not login.get('success'):
    print('Login diagnostic: HTTP ' + str(status) + ', code=' + str(login.get('code')) + ', message=' + str(login.get('message')), flush=True)
check('administrator login with generated password and CAPTCHA', status == 200 and login.get('success') and bool(login.get('result', {}).get('token')))
token = login['result']['token']
auth = {'X-Access-Token': token}
for path in ['/jeecgboot/sys/user/getUserInfo', '/jeecgboot/sys/permission/getUserPermissionByToken']:
    status, body, headers = request(path, headers=auth)
    result = json.loads(body)
    check('authenticated ' + path, status == 200 and result.get('success'))
    if 'getUserPermission' in path:
        check('site-platform menu installed', b'site-platform' in body or b'siteplatform' in body)

# Anonymous ClickHouse table access must fail; configured reader can query.
status, body, headers = request('/?query=SELECT%20count()%20FROM%20jeecg_analytics.api_call', base='http://127.0.0.1:18123')
check('ClickHouse anonymous query rejected', status in (401, 403))
basic = base64.b64encode((env['CLICKHOUSE_READER_USER'] + ':' + env['CLICKHOUSE_READER_PASSWORD']).encode()).decode()
status, body, headers = request('/?query=SELECT%20count()%20FROM%20jeecg_analytics.api_call', headers={'Authorization': 'Basic ' + basic}, base='http://127.0.0.1:18123')
check('ClickHouse authenticated reader query', status == 200 and body.strip().isdigit())
status, body, headers = request('/jeecgboot/openapi/statistics/status', headers=auth)
result = json.loads(body)
check('authenticated analytics status', status == 200 and result.get('success'))
now = int(time.time() * 1000)
status, body, headers = request('/jeecgboot/openapi/statistics/summary?start=' + str(now - 3600000) + '&end=' + str(now), headers=auth)
result = json.loads(body)
check('backend ClickHouse aggregate query', status == 200 and result.get('success'))
print('Deployment verification completed; no credentials printed.', flush=True)
