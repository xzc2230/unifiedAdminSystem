import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import * as conversions from '../src/views/packagemanagement/package-conversions.mjs';
import { parseTags, serializeTags, parseScore, weightedScore, toAreas, fromAreas, localDateTime, normalizePackageMenus, pwaPayload, legacyImageUrl, uploadedImageName } from '../src/views/packagemanagement/package-conversions.mjs';

test('tags preserve JSON storage and reject empty tags', () => {
  assert.deepEqual(parseTags('["游戏","工具"]'), ['游戏', '工具']);
  assert.equal(serializeTags([' 游戏 ', '工具']), '["游戏","工具"]');
  assert.throws(() => serializeTags([]));
  assert.throws(() => parseTags('{"x":1}'));
});
test('weighted score preserves source score1 through score5 semantics', () => {
  assert.equal(weightedScore(parseScore('{"score1":10,"score5":30}')), '4.0');
  assert.equal(weightedScore({}), '0');
  assert.equal(weightedScore({score2: 1, score3: 2}), '2.7');
});
test('region arrays and local review dates round trip', () => {
  assert.deepEqual(toAreas('US, BR,,CN'), ['US', 'BR', 'CN']);
  assert.equal(fromAreas(['US', 'BR']), 'US,BR');
  assert.equal(localDateTime(new Date(2026, 10, 9, 8, 7, 6)), '2026-11-09 08:07:06');
  assert.equal(localDateTime('2025-01-02 03:04:05'), '2025-01-02 03:04:05');
});
test('package menu redirects to first visible authorized child and leaves other roots alone', () => {
  const other = {path:'/system', children:[]};
  const full = {path:'/package-management',children:[{path:'apps'},{path:'pwa'}]};
  assert.equal(normalizePackageMenus([other,full])[0], other);
  assert.equal(normalizePackageMenus([full])[0].redirect, '/package-management/apps');
  assert.equal(normalizePackageMenus([{...full,children:[{path:'apps',meta:{hideMenu:true}},{path:'pwa'}]}])[0].redirect, '/package-management/pwa');
  assert.equal(normalizePackageMenus([{...full,children:[]}]).length, 0);
  assert.equal(normalizePackageMenus([full])[0].meta.alwaysShow, true);
  assert.equal(full.redirect, undefined);
});
test('PWA requires domain dictionary, score, tags and complete reviews without changing IDs or dates', () => {
  const value = {appSubHost:'test', reviewsRecords:[{reviewsId:9,name:'A',content:'Good',headPortraitUrl:'/a.png',createTime:'2025-01-02 03:04:05'}]};
  const result = pwaPayload(value,['Game'],{score5:5},[{value:'test',label:'.example.invalid'}],1);
  assert.equal(result.appSub,'.example.invalid');
  assert.equal(result.appType,1);
  assert.equal(result.reviewsRecords[0].reviewsId,9);
  assert.equal(result.reviewsRecords[0].createTime,'2025-01-02 03:04:05');
  assert.throws(()=>pwaPayload(value,['Game'],{score5:5},[],0));
  assert.throws(()=>pwaPayload(value,['Game'],{},[{value:'test',label:'.invalid'}],0));
  assert.throws(()=>pwaPayload({...value,reviewsRecords:[]},['Game'],{score5:5},[{value:'test',label:'.invalid'}],0));
});

const require=createRequire(import.meta.url);
test('legacy images retain profile storage and resolve historical previews without host static rewriting',()=>{
  assert.equal(legacyImageUrl('/profile/upload/a.png','/jeecg-boot'),'/jeecg-boot/package-legacy/profile/upload/a.png');
  assert.equal(legacyImageUrl('https://cdn.example.invalid/a.png','/jeecg-boot'),'https://cdn.example.invalid/a.png');
  assert.equal(uploadedImageName({code:200,fileName:'/profile/upload/a.png',url:'http://ignored.invalid/a'}),'/profile/upload/a.png');
  assert.throws(()=>uploadedImageName({code:500,msg:'failed'}));
  assert.throws(()=>uploadedImageName({code:200,url:'http://ignored.invalid/a'}));
});
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
test('all 13 page entries and business editor SFCs compile', () => {
  const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc');
  let count=0;
  function walk(directory){for(const entry of fs.readdirSync(directory,{withFileTypes:true})){
    const file=path.join(directory,entry.name);
    if(entry.isDirectory()) walk(file);
    else if(file.endsWith('.vue')){
      const {descriptor,errors}=parse(fs.readFileSync(file,'utf8'),{filename:file});
      assert.deepEqual(errors,[],file);
      const script=compileScript(descriptor,{id:file});
      const template=compileTemplate({id:file,filename:file,source:descriptor.template.content,compilerOptions:{bindingMetadata:script.bindings}});
      assert.deepEqual(template.errors,[],file); count++;
    }
  }}
  walk(path.join(root,'src/views/packagemanagement'));
  assert.equal(count,17);
});
test('editable business fields agree with backend allowlists and preserve domain special cases', async () => {
  const ts=require('typescript');
  const source=fs.readFileSync(path.join(root,'src/views/packagemanagement/package.data.ts'),'utf8');
  const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
  const {definitions}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'));
  const registry=fs.readFileSync(path.join(root,'../jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/controller/admin/PackageAdminRegistry.java'),'utf8');
  assert.equal(Object.keys(definitions).length,13);
  for(const [name,definition] of Object.entries(definitions)){
    if(name==='request-logs')continue;
    const section=registry.split(`resources.put("${definition.domain}"`)[1].split('resources.put(')[0];
    const allowed=[...section.match(/List\.of\(([^)]*)\)/s)[1].matchAll(/"([^"]+)"/g)].map(m=>m[1]);
    const actual=definition.fields.filter(f=>!f.readonly).map(f=>f.key);
    for(const key of actual) assert.ok(allowed.includes(key),`${name}.${key} must be accepted by controller`);
    if(name==='event-history')assert.deepEqual(new Set(actual),new Set(allowed));
  }
  assert.equal(definitions.store.export,false);
  assert.equal(definitions.pwa.appType,0);assert.equal(definitions.w2a.appType,1);
  assert.equal(definitions.w2a.domain,'pwa');
  assert.equal(definitions['request-logs'].add,false);
  assert.equal(definitions['app-events'].key,'eventId');assert.equal(definitions.reviews.key,'reviewsId');
  assert.equal(definitions.statistics.fields.find(f=>f.key==='paymentTotal').readonly,true);
  assert.ok(definitions['landing-counts'].filters.includes('appName'));
  assert.ok(definitions['landing-counts'].filters.includes('time'));
  assert.equal(definitions.apps.fields.find(f=>f.key==='url').required,true);
});
test('actual package bodyCell slot preserves Ant Table custom checkbox and index renderers',async()=>{
  const {createSSRApp,h,compile}=require('vue');
  const {renderToString}=require('vue/server-renderer');
  const {Table,Checkbox,Space,Image}=require('ant-design-vue');
  const source=fs.readFileSync(path.join(root,'src/views/packagemanagement/components/PackagePage.vue'),'utf8');
  const slot=source.match(/(<template #bodyCell[\s\S]*?)\s*<\/BasicTable>/)[1];
  const app=createSSRApp({
    render:compile(`<a-table :columns="columns" :data-source="rows" :pagination="false" row-key="id">${slot}</a-table>`),
    setup:()=>({columns:[{key:'customSelection',customRender:()=>h(Checkbox,{checked:true})},{key:'index',customRender:()=>h('span',{'data-index':true},'1')},{key:'appName',dataIndex:'appName'}],
      rows:[{id:1,appName:'Visible application'}],fieldsByKey:{appName:{key:'appName'}},definition:{domain:'apps'},displayValue:(_key,value)=>value}),
  });
  app.component('a-table',Table);
  app.component('a-space',Space);app.component('a-image',Image);app.directive('auth',{});
  const html=await renderToString(app);
  assert.match(html,/type="checkbox"/);
  assert.match(html,/data-index="true"/);
  assert.match(html,/Visible application/);
  assert.doesNotMatch(html,/&quot;appName&quot;/);
});
test('concurrent review removal uses identity, deduplicates requests, and preserves other comments',async()=>{
  const a={reviewsId:1},b={reviewsId:2},c={reviewsId:3};const rows=[a,b,c];
  const resolve=new Map();const calls=[];const pending=new Set();
  const remove=conversions.createReviewRemover(()=>rows,id=>{calls.push(id);return new Promise(done=>resolve.set(id,done));},pending);
  const pa=remove(a),pb=remove(b);await remove(b);
  assert.deepEqual(calls,[1,2]);assert.equal(pending.size,2);
  resolve.get(1)();await pa;resolve.get(2)();await pb;
  assert.deepEqual(rows,[c]);assert.equal(pending.size,0);
});
test('failed persisted review deletion leaves the row and permits retry',async()=>{
  const row={reviewsId:1};const rows=[row];const pending=new Set();let fails=true;
  const remove=conversions.createReviewRemover(()=>rows,async()=>{if(fails)throw new Error('server failure');},pending);
  await assert.rejects(remove(row),/server failure/);assert.deepEqual(rows,[row]);assert.equal(pending.size,0);
  fails=false;await remove(row);assert.deepEqual(rows,[]);
});
test('menu seed has one root, 13 direct pages, unique stable IDs and no automatic role grants',()=>{
  const sql=fs.readFileSync(path.join(root,'../jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/V20261109_04__package_menus.sql'),'utf8');
  const ids=[...sql.matchAll(/VALUES \('([^']+)'/g)].map(m=>m[1]);
  assert.equal(ids.length,new Set(ids).size);assert.ok(ids.every(id=>id.length<=32));
  assert.equal((sql.match(/'pkg_mgmt_root','[^']+','\/package-management\//g)||[]).length,13);
  assert.equal((sql.match(/'\/package-management','layouts\/default\/index'/g)||[]).length,1);
  assert.equal(/INSERT INTO sys_role_permission/i.test(sql),false);
  assert.equal(/package:store:export/.test(sql),false);
});
