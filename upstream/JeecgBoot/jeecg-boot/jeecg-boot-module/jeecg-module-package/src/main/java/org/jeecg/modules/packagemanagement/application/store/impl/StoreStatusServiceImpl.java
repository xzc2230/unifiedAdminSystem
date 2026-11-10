package org.jeecg.modules.packagemanagement.application.store.impl;

import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.jeecg.modules.packagemanagement.application.store.IStoreStatusService;


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
        StoreStatus storeStatus = new StoreStatus();
        List<StoreStatus> storeStatusList = storeStatusMapper.selectStoreStatusList(storeStatus);
        List<StoreStatus> filteredList = storeStatusList.stream()
                .filter(status -> status.getStatus() != 2)
                .collect(Collectors.toList());
        if (!filteredList.isEmpty()) {
            for (StoreStatus s : filteredList) {
                try {
                    Boolean isOnline = resolveOnline(s);
                    if (isOnline == null) {
                        continue;
                    }
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
                    }
                } catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Package store probe failed package={}", s.getPackageCode(), failure);
                }
            }
        }
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
            if (appleAppId == null || !appleAppId.matches("\\d+")) {
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
                return resultCount >= 1;
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("iOS store probe failed package={}", storeStatus.getPackageCode(), failure);
                return null;
            }
        }
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Store probe skipped, unknown platform package={} platform={}", storeStatus.getPackageCode(), platform);
        return null;
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
