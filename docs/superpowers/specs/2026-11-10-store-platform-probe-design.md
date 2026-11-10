# 应用平台与商店分探测设计

用户已确认：商店上架定时任务仍只跑一条。应用保存时按平台写成对应商店链接，任务按商店记录上的平台选择探测方式。现有数据全部视为安卓。一个应用只属于一个平台。安卓用包名，iOS 另填数字 Apple ID。

## 范围

改应用保存、商店记录、应用表单、商店列表，以及 `PackageStoreJob` 调用的 `StoreStatusServiceImpl.getStatus()`。

不改包统计任务。`PackagePackStatJob` 继续探测包统计里手工保存的商店链接，不读应用平台。不改商店状态含义：`0` 待探测，`1` 在架，`2` 已下架。不改客户端下发协议。

## 数据

`pkg_app_manager` 与 `pkg_store_status` 增加 `platform`，只允许 `android` 或 `ios`。iOS 再增加 `apple_app_id` 与 `store_region`。`apple_app_id` 只存数字。`store_region` 为两位小写国家码，缺省 `us`。表单下拉与白名单地区使用同一个 `app_area` 字典。安卓这两列为空。

已有应用和商店记录回填 `platform=android`，已有 Google Play 链接保持不变。

应用包名唯一改为「平台 + 包名」唯一。商店记录按「平台 + 包名」对应，不再只按包名查找。同一个包名可以各有一条安卓和一条 iOS。

迁移脚本可重复执行，不删除已有商店状态、时间和邮箱。

## 保存应用

新建或修改应用时同步商店记录。

- 安卓链接：`https://play.google.com/store/apps/details?id=` 加包名。
- iOS 链接：`https://apps.apple.com/{store_region}/app/id{apple_app_id}`。

iOS 缺少 Apple ID，或 Apple ID 含非数字，拒绝保存。安卓不接受 Apple ID 和地区。

修改平台、包名或 Apple ID 时，重写商店链接，并把该商店记录状态重置为 `0`，清空上架时间和下架时间。过审邮箱和下架邮箱保留。下一轮定时任务重新探测。已下架记录也会因此重新进入探测。

只改应用名称、备注等非商店标识字段时，不重置商店状态，也不改链接。

## 页面

应用表单用单选区分安卓和 iOS，默认安卓。选 iOS 时才显示 Apple ID 和地区。Apple ID 必填，只能是数字。地区缺省 `us`，下拉选项与白名单地区相同。

商店列表展示平台和实际探测链接。

## 定时任务

仍使用一个 `PackageStoreJob`，扫描状态不是 `2` 的商店记录。

- 安卓：对保存的 Google Play 链接做 GET。请求成功视为在架，成功判断沿用当前 `StoreProbePort`。状态 `0` 且在架变为 `1` 并写上架时间；状态 `1` 且不在架变为 `2` 并写下架时间。邮件标题保持「GP上架」「GP下架」。
- iOS：请求 `https://itunes.apple.com/lookup?id={apple_app_id}&country={store_region}`。响应中 `resultCount >= 1` 视为在架。不用 App Store 网页是否返回 200 判断。邮件标题为「App Store上架」「App Store下架」。
- 平台无法识别，或 iOS 记录缺少 Apple ID：跳过并记日志，不改状态。
- 网络失败、超时或响应无法解析：不改状态，避免一次失败被记成下架。继续处理后续记录。邮件发送失败只记日志，不回滚已经写好的状态。

状态已经是 `2` 的记录不探测。

## 验收

覆盖：已有数据回填为安卓且链接不变；安卓新建仍生成 Google Play 链接；iOS 新建生成 App Store 链接并拒绝非法 Apple ID；同一包名可同时存在两个平台；修改平台或标识后状态回到 `0`；只改名称不重置状态；任务对安卓和 iOS 使用各自判定；无法识别和网络失败不改状态；状态 `2` 不被扫描；包统计探测代码路径不被这次修改调用。
