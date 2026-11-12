import fs from 'fs-extra';
import path from 'node:path';
import { loadEnv } from 'vite';

async function copyChat() {
  const modeIndex = process.argv.indexOf('--mode');
  const mode = modeIndex >= 0 ? process.argv[modeIndex + 1] : 'production';
  const env = loadEnv(mode, process.cwd());
  const enabled = (process.env.VITE_ENABLE_AI ?? env.VITE_ENABLE_AI) === 'true';
  const destDir = path.resolve(process.cwd(), 'dist/chat');
  if (!enabled) {
    await fs.remove(destDir);
    console.log('AI disabled: chat widgets are not published.');
    return;
  }
  await fs.copy(path.resolve(process.cwd(), 'src/views/super/airag/aiapp/chat/js'), destDir);
  console.log('Chat widgets copied.');
}

copyChat().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
