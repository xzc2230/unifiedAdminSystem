import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ts = require('typescript');
const source = fs.readFileSync(path.join(import.meta.dirname, '../src/layouts/default/tabs/aiAssistantMenu.ts'), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { hasAiAssistantMenu } = await import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'));

test('tab AI entry follows the assigned assistant menu', () => {
  assert.equal(hasAiAssistantMenu([]), false);
  assert.equal(hasAiAssistantMenu([{ path: '/apps', name: '应用管理' }]), false);
  assert.equal(hasAiAssistantMenu([{ path: '/airag', name: 'AI大模型', children: [{ path: '/super/airag/aimodel/AiModelList', name: 'AI模型配置' }] }]), false);
  assert.equal(hasAiAssistantMenu([{ path: '/airag', name: 'AI大模型', children: [{ path: '/super/airag/aiapp/chat/AiChat', name: 'AI聊天', hideMenu: true }] }]), true);
  assert.equal(hasAiAssistantMenu([{ name: 'AI助手', originComponent: 'dashboard/ai/index' }]), true);
});
