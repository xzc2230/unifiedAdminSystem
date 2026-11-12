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
  assert.deepEqual(toAreas('US, BR,,CN'), ['us', 'br', 'cn']);
  assert.equal(fromAreas(['US', 'BR']), 'us,br');
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
  assert.equal(normalizePackageMenus([full])[0].meta.alwaysShow, false);
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
test('actual sidebar renderers expose package children instead of treating the root as an aggregate route',()=>{
  const ts=require('typescript');
  const utils=ts.createSourceFile('utils.ts',fs.readFileSync(path.join(root,'src/utils/common/compUtils.ts'),'utf8'),ts.ScriptTarget.Latest,true);
  const helper=utils.statements.find(node=>ts.isFunctionDeclaration(node)&&node.name?.text==='checkChildrenHidden');
  const checkChildrenHidden=new Function(ts.transpileModule(helper.getText().replace(/^export\s+/,''),{}).outputText+';return checkChildrenHidden;')();
  const children=Array.from({length:13},(_,i)=>({path:`/package-management/page${i}`,hideMenu:false,meta:{hideMenu:false}}));
  for(const incoming of [true,false,1,0]){
    const normalized=normalizePackageMenus([{path:'/package-management',alwaysShow:incoming,meta:{alwaysShow:incoming},children}])[0];
    assert.equal(checkChildrenHidden(normalized),true,'package directory must not trigger host aggregate-route hiding');
    for(const file of ['src/components/Menu/src/components/BasicSubMenuItem.vue','src/components/SimpleMenu/src/SimpleSubMenu.vue']){
      const source=fs.readFileSync(path.join(root,file),'utf8');
      const body=source.match(/function menuHasChildren\([^)]*\): boolean \{([\s\S]*?)\n      \}/)[1];
      const predicate=new Function('checkChildrenHidden','menuTreeItem',body).bind(null,checkChildrenHidden);
      assert.equal(predicate(normalized),true,file+' must render a submenu');
      assert.equal(predicate({...normalized,children:children.map(child=>({...child,hideMenu:true}))}),false,'hidden children stay hidden');
    }
  }
});

test('all package SFCs compile and required page entries and shared components are discovered', () => {
  const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc');
  const packageDirectory=path.join(root,'src/views/packagemanagement');
  const compiledFiles=new Set();
  function walk(directory){for(const entry of fs.readdirSync(directory,{withFileTypes:true})){
    const file=path.join(directory,entry.name);
    if(entry.isDirectory()) walk(file);
    else if(file.endsWith('.vue')){
      const {descriptor,errors}=parse(fs.readFileSync(file,'utf8'),{filename:file});
      assert.deepEqual(errors,[],file);
      const script=compileScript(descriptor,{id:file});
      const template=compileTemplate({id:file,filename:file,source:descriptor.template.content,compilerOptions:{bindingMetadata:script.bindings}});
      assert.deepEqual(template.errors,[],file);
      compiledFiles.add(path.relative(packageDirectory,file).split(path.sep).join('/'));
    }
  }}
  walk(packageDirectory);
  // Check coverage without preventing additional independently compiled components.
  const requiredFiles=[
    ...['apps','pwa','w2a','store','notifications','statistics','reviews','app-events','event-configs','event-errors','event-history','landing-counts','packstat','request-logs','risk','client-docs'].map(name=>`${name}/index.vue`),
    ...['calllog','docs','token'].map(name=>`openapi/${name}.vue`),
    ...['AppEditor','AppSharingModal','DevRecordPage','PackageField','PackageImageUpload','PackagePage','PwaEditor'].map(name=>`components/${name}.vue`),
  ];
  for(const file of requiredFiles) assert.ok(compiledFiles.has(file),`${file} must be discovered and compiled`);
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
  assert.equal(definitions['request-logs'].fields.find(f=>f.key==='startReqTime').label,'开始日期');
  assert.equal(definitions['request-logs'].fields.find(f=>f.key==='endReqTime').label,'结束日期');
  assert.equal(definitions['request-logs'].fields.find(f=>f.key==='startReqTime').type,'date');
  const requestLogPage=fs.readFileSync(path.join(root,'src/views/packagemanagement/components/PackagePage.vue'),'utf8');
  assert.match(requestLogPage,/RangePicker/);
  assert.match(requestLogPage,/开始日期/);
  assert.match(requestLogPage,/结束日期/);
  assert.match(requestLogPage,/Asia\/Shanghai/);
  assert.match(requestLogPage,/const compactList=\['apps','store','request-logs'\]/);
  assert.match(requestLogPage,/clickToRowSelect:!compactList/);
  assert.match(requestLogPage,/width:'180px'/);
  assert.match(requestLogPage,/width:'240px'/);
  assert.match(requestLogPage,/class="list-actions"/);
  assert.match(requestLogPage,/确认删除全部请求日志/);
  assert.match(requestLogPage,/cleanLogs\(\{\}\)/);
  assert.doesNotMatch(requestLogPage,/cleanLogs\(lastFilters\.value\)/);
  const devPage=fs.readFileSync(path.join(root,'src/views/packagemanagement/components/DevRecordPage.vue'),'utf8');
  assert.match(devPage,/domain==='risk'/);
  assert.match(devPage,/clickToRowSelect:props\.domain!=='risk'/);
  assert.match(devPage,/width:'180px'/);
  assert.match(devPage,/class="list-actions"/);
  assert.equal(definitions['app-events'].key,'eventId');assert.equal(definitions.reviews.key,'reviewsId');
  assert.equal(definitions.statistics.fields.find(f=>f.key==='paymentTotal').readonly,true);
  assert.ok(definitions['landing-counts'].filters.includes('appName'));
  assert.ok(definitions['landing-counts'].filters.includes('time'));
  assert.equal(definitions.apps.fields.find(f=>f.key==='url').required,true);
});
test('app editor offers android and ios store fields', () => {
  const ts=require('typescript');
  const source=fs.readFileSync(path.join(root,'src/views/packagemanagement/package.data.ts'),'utf8');
  const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
  return import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64')).then(({definitions})=>{
    const keys = definitions.apps.fields.map((field) => field.key);
    assert.ok(keys.includes('platform'));
    assert.ok(keys.includes('appleAppId'));
    assert.ok(keys.includes('storeRegion'));
    assert.notEqual(definitions.apps.fields.find((field) => field.key === 'appName').required, true);
    assert.equal(definitions.apps.fields.find((field) => field.key === 'platform').type, 'radio');
    assert.notEqual(definitions.apps.fields.find((field) => field.key === 'appleAppId').required, true);
    const region = definitions.apps.fields.find((field) => field.key === 'storeRegion');
    const areas = definitions.apps.fields.find((field) => field.key === 'areas');
    assert.equal(region.dict, areas.dict);
    assert.equal(region.dict, 'app_area');
    const code = definitions.apps.fields.find((field) => field.key === 'applicationCode');
    assert.equal(code.label, '默认编码');
    assert.equal(code.readonly, true);
    assert.deepEqual(definitions.apps.filters, ['packageCode']);
    assert.equal(definitions.apps.columns.includes('id'), false);
    assert.deepEqual(definitions.apps.columns.slice(0, 3), ['appName', 'platform', 'appleAppId']);
    assert.notEqual(definitions.apps.fields.find((field) => field.key === 'packageCode').immutable, true);
    assert.equal(definitions.statistics.fields.find((field) => field.key === 'packageCode').immutable, true);
    for (const hidden of ['author', 'applicationCode', 'storeRegion', 'whetherUse', 'gaidAidThreshold', 'aidDeviceThreshold', 'aidGaidSwitchThreshold', 'riskWindowSeconds', 'referrerBlacklistJson']) {
      assert.equal(definitions.apps.columns.includes(hidden), false, hidden);
    }
    assert.deepEqual(definitions.store.filters, ['packageCode', 'status', 'appleAppId']);
    const page = fs.readFileSync(path.join(root, 'src/views/packagemanagement/components/PackagePage.vue'), 'utf8');
    assert.match(page, /mode:'multiple'/);
    assert.match(page, /准备提交/);
    assert.match(page, /可供销售/);
    assert.match(page, /预购/);
    assert.match(page, /被开发者下架/);
    assert.match(page, /iOS\$\{label\}/);
    assert.match(page, /statusQuery/);
    assert.match(page, /v-if="definition\.domain !== 'apps'" v-auth="permission\('query'\)" @click="showDetail\(record\)">详情/);
    assert.equal(definitions.store.fields.some((field) => field.key === 'appleAppId'), true);
    assert.match(fs.readFileSync(path.join(root, 'src/views/packagemanagement/components/PackagePage.vue'), 'utf8'), /key!=='applicationCode'/);
  });
});
test('app editor leaves ios apple id optional and defaults the store region', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/packagemanagement/components/AppEditor.vue'), 'utf8');
  assert.doesNotMatch(source, /appleAppId' && props\.model\.platform === 'ios'/);
  assert.match(source, /只输入数字，不要带 id/);
  assert.match(source, /\['appName', 'applicationCode', 'packageCode', 'url', 'mark'\]/);
  assert.doesNotMatch(source, /创建后不可修改/);
  assert.match(source, /model\.storeRegion \|\| 'us'/);
  assert.match(source, /optionsFor\(storeRegionField\)/);
  assert.match(source, /:value="model\.areas"/);
  assert.doesNotMatch(source, /update\(areasField\.value, \[\]\)/);
  assert.match(source, /class="field-grid basic-grid"/);
  assert.match(source, /key === 'url' \|\| key === 'mark' \? 'span-all' : 'span-third'/);
  assert.match(source, /v-show="riskAdvancedOpen"/);
  assert.match(source, /高级配置/);
  assert.doesNotMatch(source, /class="full-width"\s+extra="商店查询会自动拼接/);
});
test('actual package bodyCell slot preserves Ant Table custom checkbox and index renderers',async()=>{
  const {createSSRApp,h,compile}=require('vue');
  const {renderToString}=require('vue/server-renderer');
  const {Table,Checkbox,Space,Image,Tooltip}=require('ant-design-vue');
  const source=fs.readFileSync(path.join(root,'src/views/packagemanagement/components/PackagePage.vue'),'utf8');
  const slot=source.match(/(<template #bodyCell[\s\S]*?)\s*<\/BasicTable>/)[1];
  const app=createSSRApp({
    render:compile(`<a-table :columns="columns" :data-source="rows" :pagination="false" row-key="id">${slot}</a-table>`),
    setup:()=>({columns:[{key:'customSelection',customRender:()=>h(Checkbox,{checked:true})},{key:'index',customRender:()=>h('span',{'data-index':true},'1')},{key:'appName',dataIndex:'appName'}],
      rows:[{id:1,appName:'Visible application'}],fieldsByKey:{appName:{key:'appName'}},definition:{domain:'apps'},displayValue:(_key,value)=>value}),
  });
  app.component('a-table',Table);
  app.component('a-space',Space);app.component('a-image',Image);app.component('a-tooltip',Tooltip);app.directive('auth',{});
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
  assert.match(sql,/SET NAMES utf8mb4\s*;[\s\S]*START TRANSACTION;/i,'UTF-8 menu seeds must override a latin1 client before inserting Chinese labels');
  assert.equal(ids.length,new Set(ids).size);assert.ok(ids.every(id=>id.length<=32));
  assert.equal((sql.match(/'pkg_mgmt_root','[^']+','\/package-management\//g)||[]).length,13);
  assert.equal((sql.match(/'\/package-management','LAYOUT'/g)||[]).length,1);
  assert.equal(/INSERT INTO sys_role_permission/i.test(sql),false);
  assert.equal(/package:store:export/.test(sql),false);
});
test('actual host route conversion keeps13 package children instead of producing a single-page wrapper',()=>{
  const ts=require('typescript');
  const source=fs.readFileSync(path.join(root,'src/router/helper/routeHelper.ts'),'utf8');
  // Compile the real production conversion with only component loading stubbed.
  const node=ts.createSourceFile('route.ts',source,ts.ScriptTarget.Latest,true).statements.find(node=>ts.isFunctionDeclaration(node)&&node.name?.text==='transformObjToRoute');
  const compiled=ts.transpileModule(node.getText().replace(/^export /,''),{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
  const convert=new Function('LayoutMap','LAYOUT','cloneDeep','warn','asyncImportRoute',compiled+'; return transformObjToRoute;')(new Map([['LAYOUT','layout-component']]),'layout-component',structuredClone,()=>{},()=>{});
  for(const component of ['LAYOUT','layouts/default/index','/layouts/default/index']){
    const children=Array.from({length:13},(_,i)=>({path:`/package-management/page${i}`,name:`page${i}`,component:`packagemanagement/page${i}/index`,meta:{title:`Page${i}`}}));
    const normalized=normalizePackageMenus([{path:'/package-management',name:'PackageManagement',component,meta:{title:'应用包管理'},children}]);
    const converted=convert(normalized)[0];
    assert.equal(converted.path,'/package-management');assert.equal(converted.meta.single,undefined);
    assert.equal(converted.children.length,13);assert.equal(converted.component,'layout-component');
  }
});
test('menu encoding repair restores exactly the canonical80 labels and only updates names',()=>{
  const seed=fs.readFileSync(path.join(root,'../jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/V20261109_04__package_menus.sql'),'utf8');
  const canonical=new Map([...seed.matchAll(/VALUES \('([^']+)',(?:NULL|'[^']*'),'([^']+)'/g)].map(match=>[match[1],match[2]]));
  const repair=fs.readFileSync(path.join(root,'../../../deploy/package-api/repair-menu-names.sql'),'utf8');
  assert.match(repair,/^[\x00-\x7F]*$/);
  const statements=repair.split(';').map(value=>value.trim()).filter(Boolean);
  assert.equal(statements.shift(),'SET NAMES utf8mb4');assert.equal(statements.shift(),'START TRANSACTION');assert.equal(statements.pop(),'COMMIT');
  const actual=new Map(statements.map(statement=>{
    const match=statement.match(/^UPDATE sys_permission SET name=CONVERT\(0x([0-9a-f]+) USING utf8mb4\) WHERE id=0x([0-9a-f]+)$/);
    assert.ok(match,'repair must update only names by exact ID');
    return [Buffer.from(match[2],'hex').toString('utf8'),Buffer.from(match[1],'hex').toString('utf8')];
  }));
  assert.equal(statements.length,80);assert.equal(actual.size,80);assert.deepEqual(actual,canonical);
});

test('country areas normalize legacy labels and codes without dropping unknown historical values', () => {
  assert.deepEqual(toAreas('巴西, BR,菲律賓,SG'), ['br','ph','sg']);
  assert.equal(fromAreas(['中国','PH']), 'cn,ph');
  assert.equal(fromAreas([]), '');
  assert.deepEqual(toAreas('未识别地区'), ['未识别地区']);
});

test('49 whitelist countries have unique codes, Chinese labels and identical server mappings', async () => {
  const {APP_AREA_OPTIONS,countryLabel} = await import('../src/views/packagemanagement/package-regions.mjs');
  assert.equal(APP_AREA_OPTIONS.length,49);
  assert.equal(new Set(APP_AREA_OPTIONS.map(row=>row.value)).size,49);
  assert.ok(APP_AREA_OPTIONS.every(row=>/^[a-z]{2}$/.test(row.value) && /[\u4e00-\u9fff]/.test(row.label)));
  assert.equal(countryLabel('BR'),'巴西');assert.equal(countryLabel('阿联酌'),'阿联酋');
  const client = fs.readFileSync(new URL('../src/views/packagemanagement/package-country-regions.json', import.meta.url),'utf8');
  const server = fs.readFileSync(new URL('../../jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/package-country-regions.json', import.meta.url),'utf8');
  assert.deepEqual(JSON.parse(client),JSON.parse(server));
});

test('client protocol docs list thirteen interfaces under the package menu', async () => {
  const { CLIENT_PROTOCOL_DOC } = await import('../src/views/packagemanagement/client-docs/client-docs.mjs');
  const page = fs.readFileSync(path.join(root, 'src/views/packagemanagement/client-docs/index.vue'), 'utf8');
  const menu = fs.readFileSync(path.join(root, '../jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/017-client-protocol-docs-menu.sql'), 'utf8');
  const risk = fs.readFileSync(path.join(root, 'src/views/packagemanagement/risk/index.vue'), 'utf8');
  assert.equal(CLIENT_PROTOCOL_DOC.host, 'https://apivo.attrnivo.com/');
  assert.equal(CLIENT_PROTOCOL_DOC.interfaces.length, 13);
  assert.deepEqual(CLIENT_PROTOCOL_DOC.interfaces.map((item) => item.path), [
    '/system/xxx', '/v2/api/user/login', '/v2/api/retry/xxx', '/v2/api/banner/list/xxx', '/dynamic/xx',
    '/v2/api/check/url', '/app/start', '/home/index/jsp', '/xxxxxx', '/xxx/xxxx', '/xxx/xxxx/xxx', '/xxx/xxxx/xxx/xxxx',
    '/api/v1/attribution/resolve',
  ]);
  assert.ok(CLIENT_PROTOCOL_DOC.interfaces.every((item) => item.requestExample && item.responseExample));
  const resolve = CLIENT_PROTOCOL_DOC.interfaces[12];
  assert.equal(resolve.title, '接口十三');
  assert.deepEqual(resolve.methods, ['POST']);
  assert.equal(resolve.headers, undefined);
  assert.equal(resolve.params.find((field) => field.name === 'applicationCode').desc, '包名');
  assert.match(resolve.requestExample, /"applicationCode": "com\.test"/);
  assert.doesNotMatch(resolve.requestExample, /Authorization/);
  assert.match(resolve.responseExample, /"destinationUrl": "https:\/\/www\.example\.com\/land"/);
  assert.deepEqual(resolve.fields.map((field) => field.name), [
    'clickRef', 'resolveToken', 'applicationCode', 'promotionCode', 'resolutionState', 'parameterSource', 'destinationUrl', 'resolvedAt', 'tokenExpiresAt', 'data', 'afLinkParams', 'fbLinkParams',
  ]);
  assert.match(risk, /key:'abi'[\s\S]*key:'brand'/);
  assert.match(page, /CLIENT_PROTOCOL_DOC/);
  assert.match(menu, /'pkg_menu_client_docs','pkg_mgmt_root','接口文档','\/package-management\/client-docs'/);
  assert.match(menu, /packagemanagement\/client-docs\/index/);
});
