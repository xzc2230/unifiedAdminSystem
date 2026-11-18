import { defHttp } from '/@/utils/http/axios';

enum Api {
  loginfo = '/sys/loginfo',
  visitInfo = '/sys/visitInfo',
  packageHome = '/package-admin/home/summary',
}
/**
 * 日志统计信息
 * @param params
 */
export const getLoginfo = (params) => defHttp.get({ url: Api.loginfo, params }, { isTransformResponse: false });
/**
 * 访问量信息
 * @param params
 */
export const getVisitInfo = (params) => defHttp.get({ url: Api.visitInfo, params }, { isTransformResponse: false });

export interface PackageHomeCounts {
  online: number;
  review: number;
  dropped: number;
}

export interface PackageHomeSummary {
  android: PackageHomeCounts;
  ios: PackageHomeCounts;
}

export const getPackageHomeSummary = () => defHttp.get<PackageHomeSummary>({ url: Api.packageHome }, { errorMessageMode: 'none' });
