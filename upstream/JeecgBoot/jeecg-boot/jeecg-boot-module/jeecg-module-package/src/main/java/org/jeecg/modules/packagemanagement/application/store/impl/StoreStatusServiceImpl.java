package org.jeecg.modules.packagemanagement.application.store.impl;

import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.jeecg.modules.packagemanagement.application.store.IStoreStatusService;
import org.jeecg.modules.packagemanagement.application.store.StoreStatusQuery;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * 包统计Service业务层处理
 *
 * @author ruoyi
 * @date 2023-11-04
 */
@Service
public class StoreStatusServiceImpl implements IStoreStatusService {
    private static final Pattern ITUNES_RESULT_COUNT = Pattern.compile("\"resultCount\"\\s*:\\s*(\\d+)");
    @Autowired
    private org.jeecg.modules.packagemanagement.application.port.HttpPort http;
    @Autowired
    private org.jeecg.modules.packagemanagement.application.port.StoreProbePort probe;
    @Autowired
    private org.jeecg.modules.packagemanagement.application.port.MailPort mail;
    @Autowired
    private org.jeecg.modules.packagemanagement.config.PackageManagementProperties settings;
    @Autowired
    private StoreStatusMapper storeStatusMapper;
    @Autowired
    private AppManagerMapper appManagerMapper;

    /**
     * 查询包状态
     *
     * @param id 包状态主键
     * @return 包状态
     */
    @Override
    public StoreStatus selectStoreStatusById(Long id) {
        return storeStatusMapper.selectStoreStatusById(id);
    }


    /**
     * 查询包状态列表
     *
     * @param storeStatus 包状态
     * @return 包状态
     */
    @Override
    public List<StoreStatus> selectStoreStatusList(StoreStatus storeStatus) {
        StoreStatusQuery.apply(storeStatus);
        return storeStatusMapper.selectStoreStatusList(storeStatus);
    }


    /**
     * 新增包状态
     *
     * @param storeStatus 包状态
     * @return 结果
     */
    @Override
    public int insertStoreStatus(StoreStatus storeStatus) {
        StoreStatus storeStatus1 = storeStatusMapper.selectStoreStatusByPackageName(storeStatus.getPackageCode());
        if (storeStatus1 != null) {
            return -1;
        } else {
            storeStatus.setCreateDate(DateUtils.getNowDate());
            return storeStatusMapper.insertStoreStatus(storeStatus);
        }
    }

    /**
     * 修改包状态
     *
     * @param storeStatus 包状态
     * @return 结果
     */
    @Override
    public int updateStoreStatusAndDate(StoreStatus storeStatus) {
        return storeStatusMapper.updateStoreStatusAndDate(storeStatus);
    }


    /**
     * 批量删除包状态
     *
     * @param ids 需要删除的包状态主键
     * @return 结果
     */
    @Override
    public int deleteStoreStatusByIds(Long[] ids) {
        return storeStatusMapper.deleteStoreStatusByIds(ids);
    }

    /**
     * 删除包状态信息
     *
     * @param id 包状态主键
     * @return 结果
     */
    @Override
    public int deleteStoreStatusById(Long id) {
        return storeStatusMapper.deleteStoreStatusById(id);
    }

    @Override
    public int updateStoreStatus(StoreStatus storeStatus) {
        return storeStatusMapper.updateStoreStatus(storeStatus);
    }

    @Override
    public void getStatus() {
        getStatus(null);
    }

    @Override
    public void getStatus(String platform) {
        String only = platform == null || platform.isBlank() ? null : platform.trim().toLowerCase();
        StoreStatus storeStatus = new StoreStatus();
        List<StoreStatus> storeStatusList = storeStatusMapper.selectStoreStatusList(storeStatus);
        List<StoreStatus> filteredList = storeStatusList.stream()
                .filter(status -> status.getStatus() != 2)
                .filter(status -> only == null || only.equals(appPlatform(status)))
                .collect(Collectors.toList());
        if (!filteredList.isEmpty()) {
            for (StoreStatus s : filteredList) {
                try {
                    String previousProduct = s.getProductStatus();
                    Boolean isOnline = resolveOnline(s);
                    if (isOnline == null) {
                        continue;
                    }
                    boolean productChanged = s.getProductStatus() != null && !s.getProductStatus().equals(previousProduct);
                    if (s.getStatus() == 0 && isOnline) {
                        org.slf4j.LoggerFactory.getLogger(getClass()).info("Store transition package={} status=0->1", s.getPackageCode());
                        s.setStatus(1);
                        s.setSuccessDate(DateUtils.getNowDate());
                        storeStatusMapper.updateStoreStatusAndDate(s);
                        StringBuilder stringBuffer = new StringBuilder(settings.getStoreRecipients());
                        if (s.getSuccessEmail() != null && !s.getSuccessEmail().isEmpty()) {
                            stringBuffer.append(stringBuffer.isEmpty() ? "" : ",").append(s.getSuccessEmail());
                        }
                        sendTransitionEmail(stringBuffer.toString(), listingEmailSubject(s), s.getStoreUrl());
                    } else if (s.getStatus() == 1 && !isOnline) {
                        org.slf4j.LoggerFactory.getLogger(getClass()).info("Store transition package={} status=1->2", s.getPackageCode());
                        s.setStatus(2);
                        s.setFailDate(DateUtils.getNowDate());
                        storeStatusMapper.updateStoreStatusAndDate(s);
                        StringBuilder stringBuffer = new StringBuilder(settings.getStoreRecipients());
                        if (s.getFailEmail() != null && !s.getFailEmail().isEmpty()) {
                            stringBuffer.append(stringBuffer.isEmpty() ? "" : ",").append(s.getFailEmail());
                        }
                        sendTransitionEmail(stringBuffer.toString(), delistingEmailSubject(s), s.getStoreUrl());
                    } else if (productChanged) {
                        storeStatusMapper.updateStoreStatusAndDate(s);
                    }
                } catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Package store probe failed package={}", s.getPackageCode(), failure);
                }
            }
        }
    }

    /** Application management is the platform source. A missing app keeps the store row's own platform. */
    private String appPlatform(StoreStatus row) {
        String stored = row.getPlatform() == null || row.getPlatform().isBlank() ? "android" : row.getPlatform().trim().toLowerCase();
        if (appManagerMapper == null || row.getPackageCode() == null || row.getPackageCode().isBlank()) return stored;
        AppManager exact = appManagerMapper.selectAppManagerByPlatformAndPackage(stored, row.getPackageCode());
        AppManager app = exact;
        if (app == null) {
            AppManager android = "android".equals(stored) ? null : appManagerMapper.selectAppManagerByPlatformAndPackage("android", row.getPackageCode());
            AppManager ios = "ios".equals(stored) ? null : appManagerMapper.selectAppManagerByPlatformAndPackage("ios", row.getPackageCode());
            if (android != null && ios == null) app = android;
            else if (ios != null && android == null) app = ios;
        }
        if (app == null || app.getPlatform() == null || app.getPlatform().isBlank()) return stored;
        String platform = app.getPlatform().trim().toLowerCase();
        row.setPlatform(platform);
        return platform;
    }

    private Boolean resolveOnline(StoreStatus storeStatus) {
        String platform = storeStatus.getPlatform();
        if (platform == null || platform.isEmpty() || "android".equalsIgnoreCase(platform)) {
            try {
                return probe.getStatus(storeStatus.getStoreUrl());
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Play store probe failed package={}", storeStatus.getPackageCode(), failure);
                return null;
            }
        }
        if ("ios".equalsIgnoreCase(platform)) {
            String appleAppId = storeStatus.getAppleAppId();
            if (appleAppId == null || appleAppId.isBlank()) return null;
            if (!appleAppId.matches("\\d+")) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("iOS store probe skipped, invalid apple app id package={} appleAppId={}", storeStatus.getPackageCode(), appleAppId);
                return null;
            }
            String region = storeStatus.getStoreRegion();
            if (region == null || region.isEmpty()) {
                region = "us";
            }
            try {
                String lookupUrl = "https://itunes.apple.com/lookup?id=" + appleAppId + "&country=" + region;
                var reply = http.exchange("GET", lookupUrl, null, Map.of());
                if (!reply.success()) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("iOS store probe lookup failed package={} status={}", storeStatus.getPackageCode(), reply.status());
                    return null;
                }
                Integer resultCount = parseResultCount(reply.body());
                if (resultCount == null) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("iOS store probe lookup missing resultCount package={}", storeStatus.getPackageCode());
                    return null;
                }
                if (resultCount >= 1) {
                    storeStatus.setProductStatus(preorder(reply.body()) ? "预购" : "已上架");
                    return true;
                }
                storeStatus.setProductStatus(storeStatus.getStatus() != null && storeStatus.getStatus() == 1 ? "已下架" : "待上架");
                return false;
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("iOS store probe failed package={}", storeStatus.getPackageCode(), failure);
                return null;
            }
        }
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Store probe skipped, unknown platform package={} platform={}", storeStatus.getPackageCode(), platform);
        return null;
    }

    private static boolean preorder(String body) {
        return body != null && body.replace(" ", "").contains("\"isPreorder\":true");
    }

    private static Integer parseResultCount(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = ITUNES_RESULT_COUNT.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String listingEmailSubject(StoreStatus storeStatus) {
        return "ios".equalsIgnoreCase(storeStatus.getPlatform()) ? "App Store上架" : "GP上架";
    }

    private static String delistingEmailSubject(StoreStatus storeStatus) {
        return "ios".equalsIgnoreCase(storeStatus.getPlatform()) ? "App Store下架" : "GP下架";
    }
    private void sendTransitionEmail(String recipients, String subject, String body) {
        try {
            mail.sendEmail(recipients, subject, body);
        } catch (RuntimeException failure) {
            // The source mail helper reports failure and continues with subsequent packages.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Package store transition email failed", failure);
        }
    }
}
