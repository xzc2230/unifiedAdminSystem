import { SnapshotStore } from './snapshot.mjs';
import { AddressPolicy, createProxy } from './proxy.mjs';
import { ControlClient, synchronize } from './control.mjs';

// No dependency loading, shell commands, admin database access, or browser credentials.
const env = process.env;
const integer = (name, fallback, min, max) => {
  const value = Number(env[name] || fallback);
  if (!Number.isInteger(value) || value < min || value > max) throw new Error(`Invalid ${name}`);
  return value;
};
const port = integer('SP_PORT',18090,1,65535);
const pollMs = integer('SP_POLL_MS',5000,1000,300000);
if (!env.SP_SNAPSHOT_FILE) throw new Error('SP_SNAPSHOT_FILE is required');
const store = new SnapshotStore(env.SP_SNAPSHOT_FILE,env.SP_NODE_ID);
const control = new ControlClient(env.SP_CONTROL_URL,env.SP_NODE_TOKEN);
const policy = new AddressPolicy(env.SP_ALLOWED_UPSTREAMS || '');
const server = createProxy(store,{
  policy,timeoutMs:integer('SP_TIMEOUT_MS',30000,100,300000),
  maxBodyBytes:integer('SP_MAX_BODY_BYTES',67108864,1,1073741824),externalProto:env.SP_EXTERNAL_PROTO || 'https',
});
let timer;let stopped = false;
const log = (event,details = {}) => process.stdout.write(JSON.stringify({ time:new Date().toISOString(),event,...details }) + '\n');
try { await store.restore(); } catch { log('snapshot_restore_failed'); }
async function poll() {
  try { const sites = await synchronize(store,control);log('snapshot_confirmed',{sites}); }
  catch (error) {
    log(error instanceof AggregateError ? 'snapshot_recovery_failed' : 'control_sync_failed',{
      retainedSites:store.snapshots.length,...(error instanceof AggregateError ? {requiresManualRecovery:true} : {}),
    });
  }
  if (!stopped) { timer = setTimeout(poll,pollMs);timer.unref(); }
}
server.on('error',() => { log('listener_failed');process.exitCode = 1; });
server.listen(port,env.SP_BIND || '127.0.0.1',() => log('listener_started',{port}));
await poll();
function stop() {
  if (stopped) return;
  stopped = true;clearTimeout(timer);server.close();
  setTimeout(() => { server.closeAllConnections();process.exit(0); },5000).unref();
}
process.once('SIGTERM',stop);process.once('SIGINT',stop);
