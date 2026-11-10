package org.jeecg.modules.packagemanagement.application.statistics.impl;

import org.jeecg.modules.packagemanagement.application.apps.support.PackageCountryRegions;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;
import org.jeecg.modules.packagemanagement.application.legacy.StringUtils;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.statistics.AppStatistics;
import org.jeecg.modules.packagemanagement.domain.statistics.StatisticsVO;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppStatisticsMapper;
import org.jeecg.modules.packagemanagement.application.statistics.IAppStatisticsService;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.jeecg.modules.packagemanagement.application.port.GeoPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;


/**
 * 包统计Service业务层处理
 *
 * @author ruoyi
 * @date 2023-11-04
 */
@Service
public class AppStatisticsServiceImpl implements IAppStatisticsService {
    @Autowired
    private GeoPort geo;

    @Autowired
    private AppStatisticsMapper appStatisticsMapper;
    @Autowired
    private StoreStatusMapper storeStatusMapper;

    /**
     * 查询包统计
     *
     * @param id 包统计主键
     * @return 包统计
     */
    @Override
    public AppStatistics selectAppStatisticsById(Long id) {
        return appStatisticsMapper.selectAppStatisticsById(id);
    }


    /**
     * 查询包统计列表
     *
     * @param appStatistics 包统计
     * @return 包统计
     */
    @Override
    public List<AppStatistics> selectAppStatisticsList(AppStatistics appStatistics) {
        return appStatisticsMapper.selectAppStatisticsList(appStatistics);
    }

    @Override
    public AppStatistics selectAppStatisticsByPackageName(String packageName) {
        return appStatisticsMapper.selectAppStatisticsByPackageName(packageName);
    }

    /**
     * 新增包统计
     *
     * @param appStatistics 包统计
     * @return 结果
     */
    @Override
    @Transactional
    public int insertAppStatistics(AppStatistics appStatistics) {
        appStatistics.setSubAreas(PackageCountryRegions.normalizeAreas(appStatistics.getSubAreas()));
        AppStatistics appStatistics1 = appStatisticsMapper.selectAppStatisticsByPackageName(appStatistics.getPackageCode());
        if (appStatistics1 != null) {
            return -1;
        } else {
            StoreStatus storeStatus = storeStatusMapper.selectStoreStatusByPlatformAndPackage("android", appStatistics.getPackageCode());
            if (storeStatus == null) {
                StoreStatus ss = new StoreStatus();
                ss.setStatus(0);
                ss.setPackageCode(appStatistics.getPackageCode());
                ss.setPlatform("android");
                ss.setStoreUrl("https://play.google.com/store/apps/details?id=" + appStatistics.getPackageCode());
                storeStatusMapper.insertStoreStatus(ss);
            }
            appStatistics.setCreateTime(DateUtils.getNowDate());
            // The source creation form supplied these defaults; SDK counters are server owned.
            appStatistics.setReigisterNum(0L);
            appStatistics.setPaymentNum(0L);
            appStatistics.setPaymentTotal(new BigDecimal("0.00"));
            return appStatisticsMapper.insertAppStatistics(appStatistics);
        }
    }

    /**
     * 修改包统计
     *
     * @param appStatistics 包统计
     * @return 结果
     */
    @Override
    public int updateAppStatistics(AppStatistics appStatistics) {
        appStatistics.setSubAreas(PackageCountryRegions.normalizeAreas(appStatistics.getSubAreas()));
        appStatistics.setUpdateTime(DateUtils.getNowDate());
        return appStatisticsMapper.updateAppStatistics(appStatistics);
    }

    @Override
    public Object uploadData(HttpServletRequest request) {
        String packageName = request.getParameter("packageCode");
        String register = request.getParameter("register");
        String payment = request.getParameter("payment");
        String amout = request.getParameter("amount");
        StatisticsVO statisticsVO = new StatisticsVO();
        AppStatistics appStatistics = appStatisticsMapper.selectAppStatisticsByPackageName(packageName);
        if (appStatistics != null) {
            if (StringUtils.isNotEmpty(register) && Objects.equals(register, "1")) {
                Long reigisterNum = appStatistics.getReigisterNum();
                appStatistics.setReigisterNum(reigisterNum + 1);
            }

            if (StringUtils.isNotEmpty(payment) && Objects.equals(payment, "1")) {
                Long paymentNum = appStatistics.getPaymentNum();
                appStatistics.setPaymentNum(paymentNum + 1);
            }

            if (StringUtils.isNotEmpty(amout)) {
                BigDecimal paymentTotal = appStatistics.getPaymentTotal();
                paymentTotal = paymentTotal.add(new BigDecimal(amout)).setScale(2, RoundingMode.HALF_UP);
                appStatistics.setPaymentTotal(paymentTotal);
            }

            appStatistics.setUpdateTime(DateUtils.getNowDate());
            int i = appStatisticsMapper.updateAppStatistics(appStatistics);
            if (i != -1) {
                return statisticsVO;
            } else {
                statisticsVO.setCode(-1);
                statisticsVO.setMessage("出错了,请联系管理员");
                return statisticsVO;
            }
        } else {
            statisticsVO.setCode(-2);
            statisticsVO.setMessage("包不存在,请先添加");
            return statisticsVO;
        }

    }

    @Override
    public Object getDetails(HttpServletRequest request) {
        String packageCode = request.getParameter("packageCode");
        StatisticsVO<Map> statisticsVO = new StatisticsVO<>();
        if (StringUtils.isEmpty(packageCode)) {
            statisticsVO.setCode(-1);
            return statisticsVO;
        } else {
            Map<String, Object> map = new HashMap<>();
            AppStatistics appStatistics = appStatisticsMapper.selectAppStatisticsByPackageName(packageCode);
            if (appStatistics != null) {
                System.out.println(appStatistics.toString());
                map.put("status", appStatistics.getStatus());
                map.put("packageCode", appStatistics.getPackageCode());
                String cityByIp = PackageCountryRegions.accessCode(geo.getCountryAndCountryCodeByIP(geo.getIpAddress(request)));
                if (appStatistics.getStatus() != 0) {
                    if (appStatistics.getSubAreas() != null && StringUtils.isNotEmpty(appStatistics.getSubAreas())) {
                        if (PackageCountryRegions.matches(appStatistics.getSubAreas(), cityByIp)) {
                            if (StringUtils.isNotEmpty(appStatistics.getSubUrl())) {
                                if (appStatistics.getShunt() == 1) {
                                    int i = new Random().nextInt(2);
                                    System.out.println("lllll=========" + i);
                                    if (i == 0) {
                                        map.put("url", appStatistics.getSubUrl());
                                    } else {
                                        map.put("url", appStatistics.getUrl());
                                    }
                                } else {
                                    map.put("url", appStatistics.getSubUrl());
                                }

                            } else {
                                map.put("url", appStatistics.getUrl());
                            }

                        } else {
                            map.put("url", appStatistics.getUrl());
                        }
                    } else {
                        map.put("url", appStatistics.getUrl());
                    }
                }
                statisticsVO.setData(map);

            } else {
                map.put("status", 0);
                statisticsVO.setData(map);
            }
        }


        return statisticsVO;
    }


    /**
     * 批量删除包统计
     *
     * @param ids 需要删除的包统计主键
     * @return 结果
     */
    @Override
    public int deleteAppStatisticsByIds(Long[] ids) {
        return appStatisticsMapper.deleteAppStatisticsByIds(ids);
    }

    /**
     * 删除包统计信息
     *
     * @param id 包统计主键
     * @return 结果
     */
    @Override
    public int deleteAppStatisticsById(Long id) {
        return appStatisticsMapper.deleteAppStatisticsById(id);
    }
}
