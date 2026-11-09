export function parseTags(value) {
  const tags = typeof value === 'string' ? JSON.parse(value || '[]') : value || [];
  if (!Array.isArray(tags) || tags.some(v => typeof v !== 'string')) throw new Error('标签必须是文字数组');
  return tags;
}
export function serializeTags(tags) {
  const values = tags.map(v => v.trim()).filter(Boolean);
  if (!values.length) throw new Error('请至少添加一个标签');
  return JSON.stringify(values);
}
export function parseScore(value) {
  const source = typeof value === 'string' ? JSON.parse(value || '{}') : value || {};
  return Object.fromEntries([1,2,3,4,5].map(i => [`score${i}`, Math.max(0, Number(source[`score${i}`]) || 0)]));
}
export function weightedScore(value) {
  const scores = parseScore(value);
  const total = Object.values(scores).reduce((a,b) => a+b,0);
  return total ? ([1,2,3,4,5].reduce((sum,i) => sum + scores[`score${i}`]*i,0)/total).toFixed(1) : '0';
}
export const toAreas = value => (Array.isArray(value) ? value : String(value || '').split(',')).map(v => v.trim()).filter(Boolean);
export const fromAreas = value => toAreas(value).join(',');
export function legacyImageUrl(value, backend) {
  if (!value || /^(https?:)?\/\//.test(value)) return value;
  return `${String(backend || '').replace(/\/$/,'')}/package-legacy/${String(value).replace(/^\//,'')}`;
}
export function uploadedImageName(response) {
  if (response?.code !== 200 || !response.fileName) throw new Error(response?.msg || '图片上传失败，服务器未返回文件名');
  return response.fileName;
}
export function createReviewRemover(getReviews, deletePersisted, pending = new Set()) {
  return async review => {
    const key = review.reviewsId ?? review;
    if (pending.has(key)) return false;
    pending.add(key);
    try {
      if (review.reviewsId != null) await deletePersisted(review.reviewsId);
      const rows = getReviews();
      const index = rows.findIndex(row => row === review || review.reviewsId != null && row.reviewsId === review.reviewsId);
      if (index >= 0) rows.splice(index, 1);
      return index >= 0;
    } finally {
      pending.delete(key);
    }
  };
}
export function localDateTime(value) {
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value)) return value;
  if (!value) throw new Error('请选择评论日期');
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error('评论日期无效');
  const pad = n => String(n).padStart(2,'0');
  return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
export function pwaPayload(value, tags, score, hosts, appType) {
  const host = hosts.find(v => String(v.value) === String(value.appSubHost));
  if (!host) throw new Error('域名字典 load_host 缺失或当前后缀无效，请联系管理员配置后重新选择');
  if (weightedScore(score) === '0') throw new Error('请设置非零评分');
  if (!value.reviewsRecords?.length) throw new Error('请至少添加一条评论');
  const reviewsRecords = value.reviewsRecords.map(item => {
    if (![item.name,item.content,item.headPortraitUrl].every(v=>String(v || '').trim())) throw new Error('每条评论须填写姓名、内容和头像');
    return {reviewsId:item.reviewsId, name:item.name,content:item.content,headPortraitUrl:item.headPortraitUrl,score:item.score,createTime:localDateTime(item.createTime)};
  });
  return {...value, appType, appSub:host.label, tags:serializeTags(tags),score:JSON.stringify(parseScore(score)),reviewsRecords};
}
export function normalizePackageMenus(routes) {
  return (routes || []).flatMap(route => {
    if (route.path !== '/package-management') return [route];
    const visible = (route.children || []).filter(child => !child.meta?.hideMenu && !child.meta?.ignoreRoute && !child.hidden);
    if (!visible.length) return [];
    const path = visible[0].path;
    return [{...route,alwaysShow:true,redirect:path.startsWith('/') ? path : `/package-management/${path}`,meta:{...route.meta,alwaysShow:true,hideMenu:false}}];
  });
}
