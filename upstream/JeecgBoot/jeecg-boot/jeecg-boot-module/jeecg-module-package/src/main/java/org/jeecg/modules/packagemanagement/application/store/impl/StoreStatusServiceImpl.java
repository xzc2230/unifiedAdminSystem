package org.jeecg.modules.packagemanagement.application.store.impl;

import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.jeecg.modules.packagemanagement.application.store.IStoreStatusService;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 包统计Service业务层处理
 *
 * @author ruoyi
 * @date 2023-11-04
 */
@Service
public class StoreStatusServiceImpl implements IStoreStatusService {
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
                boolean isOnline = probe.getStatus(s.getStoreUrl());
                if (s.getStatus() == 0 && isOnline) {
                    s.setStatus(1);
                    s.setSuccessDate(DateUtils.getNowDate());
                    storeStatusMapper.updateStoreStatusAndDate(s);
                    StringBuilder stringBuffer = new StringBuilder(settings.getStoreRecipients());
                    if (s.getSuccessEmail() != null && !s.getSuccessEmail().isEmpty()) {
                        stringBuffer.append(stringBuffer.isEmpty() ? "" : ",").append(s.getSuccessEmail());
                    }
                    sendTransitionEmail(stringBuffer.toString(), "GP上架", s.getStoreUrl());
                } else if (s.getStatus() == 1 && !isOnline) {
                    s.setStatus(2);
                    s.setFailDate(DateUtils.getNowDate());
                    storeStatusMapper.updateStoreStatusAndDate(s);
                    StringBuilder stringBuffer = new StringBuilder(settings.getStoreRecipients());
                    if (s.getFailEmail() != null && !s.getFailEmail().isEmpty()) {
                        stringBuffer.append(stringBuffer.isEmpty() ? "" : ",").append(s.getFailEmail());
                    }
                    sendTransitionEmail(stringBuffer.toString(), "GP下架", s.getStoreUrl());
                }
            }
        }
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
