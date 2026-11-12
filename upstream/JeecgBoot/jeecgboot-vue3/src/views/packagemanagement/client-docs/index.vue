<template>
  <div class="client-docs">
    <header class="mast">
      <p class="eyebrow">{{ doc.section }}</p>
      <h1>{{ doc.title }}</h1>
    </header>
    <div class="layout">
      <nav class="rail" aria-label="接口目录">
        <a v-for="item in doc.interfaces" :key="item.key" :href="`#protocol-${item.key}`">{{ item.title }}</a>
      </nav>
      <div class="sheets">
        <article v-for="item in doc.interfaces" :id="`protocol-${item.key}`" :key="item.key" class="sheet">
          <h2>{{ item.title }}</h2>
          <p class="path"><span>path</span><code>{{ item.path }}</code></p>
          <p v-if="item.pathNote" class="note">{{ item.pathNote }}</p>
          <p v-if="item.requestNote" class="note">{{ item.requestNote }}</p>
          <p class="methods">请求方式 <strong>{{ item.methods.join('、') }}</strong></p>

          <template v-if="item.headers?.length">
            <h3>Header</h3>
            <a-table :columns="paramColumns" :data-source="item.headers" :pagination="false" size="small" row-key="name" />
          </template>

          <h3>请求参数</h3>
          <a-table :columns="paramColumns" :data-source="item.params" :pagination="false" size="small" row-key="name" />
          <ul v-if="item.notes?.length" class="notes">
            <li v-for="note in item.notes" :key="note">{{ note }}</li>
          </ul>

          <template v-if="item.ua?.length">
            <h3>客户端 UA</h3>
            <a-table :columns="uaColumns" :data-source="item.ua" :pagination="false" size="small" row-key="name" />
            <pre>{{ item.uaJoin }}</pre>
            <p class="note">{{ item.uaNote }}</p>
          </template>

          <template v-if="item.regions">
            <h3>多地区多链接（备注配置）</h3>
            <p class="note">地区中文以白名单名称为准</p>
            <pre>{{ item.regions }}</pre>
          </template>

          <template v-if="item.outcomes?.length">
            <h3>返回</h3>
            <a-table :columns="outcomeColumns" :data-source="item.outcomes" :pagination="false" size="small" row-key="when" />
          </template>

          <template v-if="item.requestExample">
            <h3>请求示例</h3>
            <pre>{{ item.requestExample }}</pre>
          </template>
          <template v-if="item.responseExample">
            <h3>响应示例</h3>
            <pre>{{ item.responseExample }}</pre>
          </template>
          <template v-if="item.responseDecoded">
            <h3>解密后</h3>
            <pre>{{ item.responseDecoded }}</pre>
          </template>

          <template v-if="item.fields?.length">
            <h3>参数说明</h3>
            <a-table :columns="fieldColumns" :data-source="item.fields" :pagination="false" size="small" row-key="name" />
          </template>

          <p v-if="item.aes" class="note">AES 密钥通过配置应用名称。默认密钥 <code>{{ item.aes }}</code></p>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { CLIENT_PROTOCOL_DOC } from './client-docs.mjs';

  const doc = CLIENT_PROTOCOL_DOC;
  const paramColumns = [
    { title: '名称', dataIndex: 'name', width: 140 },
    { title: '描述', dataIndex: 'desc' },
    { title: '示例', dataIndex: 'example' },
    { title: '说明', dataIndex: 'detail' },
  ];
  const fieldColumns = [
    { title: '名称', dataIndex: 'name', width: 140 },
    { title: '描述', dataIndex: 'desc' },
    { title: '说明', dataIndex: 'detail' },
  ];
  const uaColumns = [
    { title: '字段', dataIndex: 'name', width: 80 },
    { title: '含义', dataIndex: 'desc', width: 120 },
    { title: '示例', dataIndex: 'example', width: 160 },
    { title: '说明', dataIndex: 'detail' },
  ];
  const outcomeColumns = [
    { title: '开关', dataIndex: 'when', width: 120 },
    { title: '返回', dataIndex: 'result' },
  ];
</script>

<style scoped>
  .client-docs {
    padding: 20px 24px 48px;
    color: #1c2430;
    background: #f4f6f8;
    min-height: 100%;
  }
  .mast {
    margin-bottom: 20px;
    padding-bottom: 16px;
    border-bottom: 1px solid #d5dbe3;
  }
  .eyebrow {
    margin: 0 0 4px;
    color: #5c6b7a;
    font-size: 12px;
    letter-spacing: 0.08em;
  }
  h1 {
    margin: 0;
    font-size: 28px;
    font-weight: 650;
    letter-spacing: -0.02em;
  }
  .layout {
    display: grid;
    grid-template-columns: 196px minmax(0, 1fr);
    gap: 20px;
    align-items: start;
  }
  .rail {
    position: sticky;
    top: 12px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 8px;
    background: #fff;
    border: 1px solid #d5dbe3;
  }
  .rail a {
    padding: 6px 8px;
    color: #243140;
    text-decoration: none;
  }
  .rail a:hover,
  .rail a:focus-visible {
    background: #e7f3ee;
    color: #0b6e4f;
  }
  .sheet {
    margin-bottom: 16px;
    padding: 18px 20px 8px;
    background: #fff;
    border: 1px solid #d5dbe3;
    scroll-margin-top: 12px;
  }
  h2 {
    margin: 0 0 12px;
    font-size: 18px;
  }
  h3 {
    margin: 16px 0 8px;
    font-size: 14px;
  }
  .path {
    display: flex;
    gap: 8px;
    align-items: baseline;
    margin: 0;
  }
  .path span {
    color: #5c6b7a;
    font-size: 12px;
  }
  code,
  pre {
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  }
  .path code {
    color: #0b6e4f;
    font-size: 15px;
  }
  pre {
    margin: 8px 0 12px;
    padding: 10px 12px;
    overflow-x: auto;
    color: #1c2430;
    background: #f4f6f8;
    border-left: 3px solid #0b6e4f;
  }
  .methods,
  .note,
  .notes,
  .examples {
    color: #3d4b5c;
  }
  .note {
    margin: 6px 0;
  }
  .examples,
  .notes {
    margin: 8px 0 12px;
    padding-left: 18px;
  }
  .sheet :deep(.ant-table-cell) {
    white-space: pre-line;
  }
  @media (max-width: 860px) {
    .layout {
      grid-template-columns: 1fr;
    }
    .rail {
      position: static;
      flex-direction: row;
      flex-wrap: wrap;
    }
  }
</style>
