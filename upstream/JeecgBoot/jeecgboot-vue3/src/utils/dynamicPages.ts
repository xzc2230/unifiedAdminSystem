// 获取所有动态页面(views目录下的所有vue文件和tsx文件)
const allFiles = import.meta.glob(
  [
    '../views/**/*.{vue,tsx}',// 获取所有vue和tsx文件
    // 排除特定文件夹
    '!../views/system/approvalrole/compoments/**',
    '!../views/super/airag/**',
    '!../views/dashboard/ai/**',
  ]
);
// 合并所有动态页面
const aiFiles = import.meta.env.VITE_ENABLE_AI === 'true'
  ? import.meta.glob(['../views/super/airag/**/*.{vue,tsx}', '../views/dashboard/ai/**/*.{vue,tsx}'])
  : {};
export const dynamicPages = { ...allFiles, ...aiFiles };
