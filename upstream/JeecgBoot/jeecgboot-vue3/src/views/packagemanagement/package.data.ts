import type { PackageDefinition, PackageField } from '/@/types/packagemanagement';
const field = (key: string,label: string,extra: Partial<PackageField> = {}): PackageField => ({key,label,...extra});
const status = field('status','开关',{type:'dict',dict:'app_status'});
const pkg = field('packageCode','包名',{required:true,immutable:true});
const pwaFields = [
  field('appName','应用名称',{required:true}),field('appIcon','应用图标',{type:'image',required:true}),
  field('appAdvertising','应用五图',{type:'image',required:true,max:5}),field('appDescription','应用描述',{type:'textarea',required:true}),
  field('appLanguage','应用语言',{type:'dict',dict:'country_name',required:true}),field('appHost','推广子域名',{required:true}),
  field('appSubHost','域名后缀',{type:'dict',dict:'load_host',required:true}),field('appUrl','包网链接',{required:true}),
  field('appEvent','应用事件',{type:'dict',dict:'event_name'}),field('appEventConf','事件配置'),
  field('downloadCount','下载量',{type:'number',required:true}),field('reviews','评论数',{type:'number',required:true}),
];
const historyLabels = {
  eventName:'事件名称',eventTime:'事件时间',eventSource:'事件来源',mediaSource:'媒体来源',campaign:'广告活动',
  afSub1:'子参数 1',afSub2:'子参数 2',afSub3:'子参数 3',region:'区域',countryCode:'国家代码',state:'州省',city:'城市',
  postalCode:'邮编',dma:'市场区域',ip:'IP 地址',wifi:'无线网络',operator:'运营商',language:'语言',appsflyerId:'AppsFlyer ID',
  platform:'平台',osVersion:'系统版本',appVersion:'应用版本',sdkVersion:'SDK 版本',appId:'应用 ID',appName:'应用名称',bundleId:'包 ID',
  isRetargeting:'再营销',userAgent:'用户代理',httpReferrer:'来源地址',originalUrl:'原始地址',deviceDownloadTime:'设备下载时间',
  conversionType:'转化类型',deviceModel:'设备型号',jsonStr:'事件内容',path:'路径',status:'同步状态',packageName:'包名',
  selected_currency:'币种',event_revenue_usd:'收入（美元）',
};
const historyFields = Object.entries(historyLabels).map(([key,label]) => field(key,label,{
  type:['jsonStr','originalUrl','userAgent'].includes(key)?'textarea':key==='status'?'number':'text',required:['path','status'].includes(key),
}));
export const definitions: Record<string,PackageDefinition> = {
  apps:{domain:'apps',title:'应用管理',filters:['packageCode'],columns:['id','appName','platform','appleAppId','storeRegion','packageCode','url','status','whetherRestrict','areas','whetherUs','whetherUse','mark','userName','updateDate'],fields:[field('id','应用ID',{readonly:true}),field('appName','应用名称',{required:true}),field('applicationCode','默认编码',{readonly:true}),field('author','出包人'),
    field('platform','平台',{type:'radio',options:[{value:'android',label:'安卓'},{value:'ios',label:'iOS'}],required:true} as Partial<PackageField>),
    field('appleAppId','Apple ID'),field('storeRegion','商店地区',{type:'dict',dict:'app_area'}),pkg,field('url','链接',{required:true}),status,
    field('whetherRestrict','白名单',{type:'dict',dict:'app_where'}),field('areas','白名单地区',{type:'dict',dict:'app_area',multiple:true}),
    field('whetherUs','禁用美国',{type:'dict',dict:'app_where'}),field('whetherUse','是否自用',{type:'dict',dict:'app_where'}),field('mark','备注',{type:'textarea'}),
    field('gaidAidThreshold','同一 GAID 关联 AID 阈值',{type:'number'}),field('aidDeviceThreshold','同一 AID 设备数阈值',{type:'number'}),
    field('aidGaidSwitchThreshold','同一 AID 的 GAID 切换阈值',{type:'number'}),field('riskWindowSeconds','风控计数窗口（秒）',{type:'number'}),
    field('referrerBlacklistJson','InstallReferrer 黑名单模板（JSON）',{type:'textarea'}),field('userName','归属用户',{readonly:true}),field('updateDate','更新时间',{type:'datetime',readonly:true})]},
  store:{domain:'store',title:'上架状态',export:false,filters:['packageCode','status','appleAppId'],fields:[{...pkg,immutable:false},field('platform','平台',{readonly:true,options:[{value:'android',label:'安卓'},{value:'ios',label:'iOS'}]}),field('appleAppId','Apple ID',{readonly:true}),field('status','上架状态',{type:'number'}),
    field('successEmail','过审邮箱'),field('failEmail','下架邮箱'),field('storeUrl','市场链接',{readonly:true}),
    field('successDate','过审日期',{readonly:true}),field('failDate','下架日期',{readonly:true})]},
  statistics:{domain:'statistics',title:'应用统计',filters:['packageCode','subAreas'],fields:[pkg,status,field('url','主链接'),field('shunt','是否分流',{type:'switch'}),field('subAreas','隐藏地区',{type:'dict',dict:'app_area'}),field('subUrl','隐藏链接'),
    field('reigisterNum','注册总数',{readonly:true}),field('paymentNum','充值总数',{readonly:true}),field('paymentTotal','充值总额',{readonly:true})]},
  'event-configs':{domain:'event-configs',title:'事件配置',filters:['packageId'],fields:[field('appName','应用名称',{required:true}),field('packageId','包名',{required:true}),field('packageToken','FB token',{required:true}),field('packagePixel','FB pixel',{required:true}),field('url','落地页地址')]},
  'event-history':{domain:'event-history',title:'事件历史',filters:historyFields.map(v=>v.key),fields:historyFields},
  'event-errors':{domain:'event-errors',title:'事件错误',filters:['errorPackage'],fields:[field('errorPackage','包名'),field('errorMessage','错误消息'),field('errorContent','错误内容',{type:'textarea'})]},
  pwa:{domain:'pwa',title:'PWA 管理',appType:0,filters:['appName'],fields:pwaFields,columns:['appName','appIcon','appAdvertising','appDescription','appLanguage','appHost','appUrl','appEvent','appEventConf']},
  w2a:{domain:'pwa',title:'W2A 管理',appType:1,filters:['appName'],fields:[...pwaFields,field('downloadUrl','下载链接',{required:true})],columns:['appName','appIcon','appAdvertising','appLanguage','appHost','appUrl','downloadUrl']},
  'landing-counts':{domain:'landing-counts',title:'落地页访问',filters:['appName','time','hostName'],fields:[field('hostName','域名'),field('appName','应用名称'),field('time','日期',{type:'date'}),field('access','访问量',{type:'number'}),field('download','下载量',{type:'number'})]},
  'app-events':{domain:'app-events',title:'应用事件',key:'eventId',filters:['appId','hostName','eventType','eventTypeName','deviceNo','ip'],fields:[field('appId','应用 ID',{type:'number'}),field('hostName','域名'),field('eventType','事件类型',{type:'number'}),field('eventTypeName','事件名称'),field('deviceNo','设备编号'),field('ip','IP 地址'),field('createTime','创建时间',{readonly:true})]},
  'request-logs':{domain:'request-logs',title:'请求日志',add:false,edit:false,filters:['packageName','startReqTime','endReqTime'],fields:[field('packageName','包名'),field('reqPath','请求地址'),field('reqIp','请求 IP'),field('reqParams','请求参数',{type:'textarea'}),field('reqHeards','请求头',{type:'textarea'}),field('reqMethod','请求方式'),field('resultMsg','响应内容',{type:'textarea'}),field('errorMsg','异常详情',{type:'textarea'}),field('reqTime','请求时间',{type:'datetime'}),field('isMobile','移动设备'),field('startReqTime','开始时间',{type:'datetime',readonly:true}),field('endReqTime','结束时间',{type:'datetime',readonly:true})],columns:['packageName','reqPath','reqIp','reqParams','reqMethod','resultMsg','errorMsg','reqTime']},
  notifications:{domain:'notifications',title:'消息推送',filters:['appName'],fields:[field('appId','接收应用',{type:'parent',required:true}),field('appName','应用名称',{readonly:true}),field('appIcon','应用图标',{readonly:true,type:'image'}),field('appHost','应用域名',{readonly:true}),field('notifyTitle','通知标题',{required:true}),field('notifyMessage','通知内容',{required:true,type:'textarea'}),field('notifyIcon','通知图标',{type:'image'}),field('createTime','创建时间',{readonly:true})]},
  reviews:{domain:'reviews',title:'评论管理',key:'reviewsId',filters:['appId','name','content'],fields:[field('appId','所属应用',{type:'parent',required:true}),field('headPortraitUrl','头像',{type:'image',required:true}),field('name','姓名',{required:true}),field('content','评论内容',{type:'textarea',required:true}),field('score','评分',{type:'number'}),field('createTime','创建时间',{readonly:true})]},
};
