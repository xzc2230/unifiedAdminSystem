import test from 'node:test';
import assert from 'node:assert/strict';
import * as dev from '../src/views/packagemanagement/package-dev.mjs';

test('actual application status options retain attribution and debug without a complete host dictionary',async()=>{
 const fs=await import('node:fs');
 const ts=await import('typescript');
 const page=fs.readFileSync(new URL('../src/views/packagemanagement/components/PackagePage.vue',import.meta.url),'utf8');
 const source=page.match(/function optionsFor\(field:[\s\S]*?\n\}/)[0];
 const js=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText;
 const select=(domain,dicts)=>new Function('definition','dicts','parentApps','APP_STATUS_OPTIONS',js+';return optionsFor;')({domain},dicts,{value:[]},dev.APP_STATUS_OPTIONS);
 const field={key:'status',type:'dict',dict:'app_status'};
 for(const dicts of [{},{app_status:[{value:0,label:'关'},{value:1,label:'开'}]}]){
  const options=select('apps',dicts)(field);
  assert.deepEqual(options.map(({value,label})=>({value,label})),[{value:0,label:'全关'},{value:1,label:'全开'},{value:2,label:'归因'},{value:3,label:'调试'}]);
  for(const status of [2,3])assert.deepEqual(dev.appEditPayload({status},['package:apps:edit'],true,{status:0}),{status});
 }
 assert.deepEqual(select('store',{})(field).map(o=>o.value),[0,1,2]);
 const other=[{value:9,label:'自定义状态'}];
 assert.equal(select('statistics',{app_status:other})(field),other);
});
test('field permissions preserve module fallback and restrict explicitly granted fields',()=>{
 const access=['package:apps:edit','package:apps:query'];
 assert.equal(dev.canAppField(access,'edit','url'),true);
 assert.equal(dev.canAppField([...access,'package:apps:editField:status'],'edit','url'),false);
 assert.equal(dev.canAppField([...access,'package:apps:editField:status'],'edit','platform'),true);
 assert.equal(dev.canAppField([...access,'package:apps:queryField:appName'],'query','appleAppId'),true);
 assert.equal(dev.canAppField([...access,'package:apps:editField:status'],'edit','status'),true);
 assert.equal(dev.canAppField(['*:*:*'],'query','url'),true);
 assert.equal(dev.canAppField([],'edit','url'),false);
 assert.equal(dev.canAppField(['package:apps:query','package:apps:queryField:updateDate'],'query','updateTime'),true);
 assert.deepEqual(dev.appEditPayload({url:'secret',status:2,packageCode:'old'},['package:apps:edit','package:apps:editField:status'],true),{status:2});
});
test('domain nameserver copy puts each apex and its names on one line', () => {
 assert.equal(dev.formatDomainNameservers([
  {apex:'vtsqzm.com', nameservers:'addyson.ns.cloudflare.com,harvey.ns.cloudflare.com'},
  {apex:'ucawkr.com', nameservers:'addyson.ns.cloudflare.com,harvey.ns.cloudflare.com'},
  {apex:'broken.com', error:'域名已存在'},
 ]), 'vtsqzm.com addyson.ns.cloudflare.com harvey.ns.cloudflare.com\nucawkr.com addyson.ns.cloudflare.com harvey.ns.cloudflare.com');
});
test('packstat durations and payload retain date and replenish semantics',()=>{
 assert.equal(dev.displayDuration({passDate:'2026-11-01',dropDate:'2026-11-10'},'aliveDays','passDate'),9);
 assert.equal(dev.displayDuration({aliveDays:0},'aliveDays','passDate'),0);
 assert.equal(dev.displayDuration({passDate:'2026-11-01'},'aliveDays','passDate'),'');
 assert.equal(dev.packstatPayload({isReplenish:0,replenishUrl:'discard',customer:''}).replenishUrl,null);
 assert.throws(()=>dev.packstatPayload({isReplenish:1,replenishUrl:''}),/补包链接/);
 assert.equal(dev.summaryRows({s7:3,u7:2,s15:1})[0].d7,3);
 assert.equal(dev.summaryRows({noRecord:true}).length,0);
});
test('OpenAPI test requests are encoded and validated',()=>{
 assert.deepEqual(dev.openApiRequest('detail',{id:'a/b',token:' t '}),{method:'GET',path:'/openapi/manager/a%2Fb',headers:{'X-Api-Token':'t','Content-Type':'application/json'}});
 assert.throws(()=>dev.openApiRequest('add',{token:'t',body:'invalid'}),/JSON/);
 assert.throws(()=>dev.openApiRequest('list',{token:''}),/Token/);
 assert.equal(dev.maskToken('abcdefgh12345678'),'abcd********5678');
 assert.equal(dev.prettyJson('{"a":1}'),'{\n  "a": 1\n}');
});

import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
const require=createRequire(import.meta.url);
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
test('dev CRUD HTTP adapter sends server contracts and automatic blacklist parameters',async()=>{
 const ts=require('typescript');const calls=[];
 const http=Object.fromEntries(['get','post','put','delete'].map(method=>[method,(...args)=>{calls.push({method,args});return Promise.resolve({});}]));
 const source=fs.readFileSync(path.join(root,'src/api/packagemanagement/dev.ts'),'utf8').replace(/import[^;]+;/g,'');
 const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
 const exports={};new Function('exports','defHttp',compiled)(exports,http);
 await exports.devApi('packstat').save({storeUrl:'https://example.com'},10);
 assert.deepEqual(calls.at(-1),{method:'put',args:[{url:'/package-admin/packstat',data:{storeUrl:'https://example.com',id:10}}]});
 await exports.devApi('risk/brand').remove(['a/b',2]);assert.equal(calls.at(-1).args[0].url,'/package-admin/risk/brand/a%2Fb,2');
 await exports.autoBlacklist('com.test','aid');assert.deepEqual(calls.at(-1).args[0].params,{pkg:'com.test',type:'aid'});
 await exports.removeAutoBlacklist('com.test','aid','value');assert.deepEqual(calls.at(-1),{method:'delete',args:[{url:'/package-admin/risk/auto',params:{pkg:'com.test',type:'aid',value:'value'}},{joinParamsToUrl:true}]});
});
test('actual dev table slot masks secrets while preserving ordinary Ant custom cells',async()=>{
 const {createSSRApp,h,compile}=require('vue');const {renderToString}=require('vue/server-renderer');const {Table,Space,Tooltip}=require('ant-design-vue');
 const source=fs.readFileSync(path.join(root,'src/views/packagemanagement/components/DevRecordPage.vue'),'utf8');
 const slot=source.match(/(<template #bodyCell[\s\S]*?)\s*<\/BasicTable>/)[1];
 const app=createSSRApp({render:compile(`<a-table :columns="columns" :data-source="rows" :pagination="false" row-key="id">${slot}</a-table>`),setup:()=>({columns:[{key:'token',dataIndex:'token'},{key:'index',customRender:()=>h('span',{'data-custom':true},'1')}],rows:[{id:1,token:'abcdefgh12345678'}],maskToken:dev.maskToken,prettyJson:dev.prettyJson,path:'openapi/token'})});
 app.component('a-table',Table);app.component('a-space',Space);app.component('a-tooltip',Tooltip);app.directive('auth',{});
 const html=await renderToString(app);assert.match(html,/abcd\*{8}5678/);assert.doesNotMatch(html,/abcdefgh12345678/);assert.match(html,/data-custom="true"/);
});
test('dev menu adds exactly five children and field permissions without role grants',()=>{
 const sql=fs.readFileSync(path.join(root,'../jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/011-dev-menu.sql'),'utf8');
 assert.match(sql,/SET NAMES utf8mb4;[\s\S]*START TRANSACTION;/);
 const ids=[...sql.matchAll(/VALUES \('([^']+)'/g)].map(m=>m[1]);assert.equal(ids.length,44);assert.equal(new Set(ids).size,44);assert.ok(ids.every(id=>id.length<=32));
 assert.equal((sql.match(/'pkg_mgmt_root','[^']+','\/package-management\//g)||[]).length,5);
 assert.equal((sql.match(/'package:apps:editField:/g)||[]).length,14);assert.equal((sql.match(/'package:apps:queryField:/g)||[]).length,11);
 assert.doesNotMatch(sql,/INSERT INTO sys_role_permission/i);
});

test('redacted application details never become default writes when saving another field',()=>{
 const permissions=['package:apps:edit','package:apps:query','package:apps:queryField:appName'];
 const initial={appName:'Visible',packageCode:null,url:null,areas:[],status:null};
 assert.deepEqual(dev.appEditPayload({...initial,appName:'Changed'},permissions,true,initial),{appName:'Changed'});
 const limited=[...permissions,'package:apps:editField:areas','package:apps:editField:appName'];
 assert.deepEqual(dev.appEditPayload({...initial,appName:'Changed'},limited,true,initial),{appName:'Changed'});
 assert.deepEqual(dev.appEditPayload({...initial,areas:['BR']},limited,true,initial),{areas:['BR']});
 assert.deepEqual(dev.appEditPayload({...initial},limited,true,initial,['areas']),{areas:[]});
 assert.deepEqual(dev.appEditPayload({...initial,url:'https://example.com'},limited,true,initial),{});
 assert.deepEqual(dev.appEditPayload({...initial,packageCode:'changed'},permissions,true,initial),{packageCode:'changed'});
 assert.equal(dev.appFieldRequired({key:'packageCode',required:true,immutable:true},true),false);
 assert.equal(dev.appFieldRequired({key:'url',required:true},true,false),false);
 assert.equal(dev.appFieldRequired({key:'url',required:true},true,true),true);
 assert.equal(dev.appFieldRequired({key:'packageCode',required:true,immutable:true},false),true);
});
