package org.jeecg.modules.packagemanagement.application.event.impl;


import org.jeecg.modules.packagemanagement.application.legacy.StringUtils;
import org.jeecg.modules.packagemanagement.domain.event.EventConfig;
import org.jeecg.modules.packagemanagement.domain.event.EventError;
import org.jeecg.modules.packagemanagement.domain.event.EvnetHistory;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.EventConfigMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.EventErrorMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.EvnetHistoryMapper;
import org.jeecg.modules.packagemanagement.application.event.IEvnetHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * af事件记录Service业务层处理
 *
 * @author ruoyi
 * @date 2024-04-16
 */
@Service
public class EvnetHistoryServiceImpl implements IEvnetHistoryService {
    @Autowired
    private org.jeecg.modules.packagemanagement.application.port.FacebookPort facebook;
    @Autowired
    private EvnetHistoryMapper evnetHistoryMapper;
    @Autowired
    private EventErrorMapper eventErrorMapper;

    @Autowired
    private EventConfigMapper eventConfigMapper;

    /**
     * 查询af事件记录
     *
     * @param id af事件记录主键
     * @return af事件记录
     */
    @Override
    public EvnetHistory selectEvnetHistoryById(Long id) {
        return evnetHistoryMapper.selectEvnetHistoryById(id);
    }

    /**
     * 查询af事件记录列表
     *
     * @param evnetHistory af事件记录
     * @return af事件记录
     */
    @Override
    public List<EvnetHistory> selectEvnetHistoryList(EvnetHistory evnetHistory) {
        return evnetHistoryMapper.selectEvnetHistoryList(evnetHistory);
    }

    /**
     * 新增af事件记录
     *
     * @param evnetHistory af事件记录
     * @return 结果
     */
    @Override
    public int insertEvnetHistory(EvnetHistory evnetHistory) {
        return evnetHistoryMapper.insertEvnetHistory(evnetHistory);
    }

    /**
     * 修改af事件记录
     *
     * @param evnetHistory af事件记录
     * @return 结果
     */
    @Override
    public int updateEvnetHistory(EvnetHistory evnetHistory) {
        return evnetHistoryMapper.updateEvnetHistory(evnetHistory);
    }

    /**
     * 批量删除af事件记录
     *
     * @param ids 需要删除的af事件记录主键
     * @return 结果
     */
    @Override
    public int deleteEvnetHistoryByIds(Long[] ids) {
        return evnetHistoryMapper.deleteEvnetHistoryByIds(ids);
    }

    /**
     * 删除af事件记录信息
     *
     * @param id af事件记录主键
     * @return 结果
     */
    @Override
    public int deleteEvnetHistoryById(Long id) {
        return evnetHistoryMapper.deleteEvnetHistoryById(id);
    }

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.jeecg.modules.packagemanagement.infrastructure.identity.PackagePublicOwnership publicOwnership;
    @Override
    @org.springframework.transaction.annotation.Transactional
    public void fbEvent(String packageId, EvnetHistory event) {
        event.setPackageName(packageId);
        evnetHistoryMapper.insertEvnetHistory(event);
        if(publicOwnership!=null)publicOwnership.fromPackage("pkg_event_history",event.getId(),packageId);
        String originalUrl = event.getOriginalUrl();
        System.out.println(originalUrl);
        try {
            URL url = new URL(originalUrl);
            Map<String, String> params = new HashMap<>();
            String query = url.getQuery();
            if (query != null) {
                String[] split = query.split("&");
                for (String s : split) {
                    String[] split1 = s.split("=");
                    String key = split1[0];
                    String value = split1[1];
                    params.put(key, value);
                }
            }
//            fbLog(packageId, event, params);
            if (!params.isEmpty()) {
                if (StringUtils.isNotEmpty(event.getIp()) || StringUtils.isNotEmpty(event.getUserAgent())) {
                    List<EvnetHistory> evnetHistories = evnetHistoryMapper.selectEvnetHistoryList(null);
                    EvnetHistory fbEvent2 = evnetHistories.get(evnetHistories.size() - 1);
                    fbEvent2.setIp(event.getIp());
                    fbEvent2.setUserAgent(event.getUserAgent());
                    fbEvent2.setOriginalUrl(event.getOriginalUrl());
                    fbLog(packageId, event, params);
                } else {
                    System.out.println("ip is empty");
                }
            } else {
                System.out.println("fbc not found");
            }
        } catch (Exception e) {
            System.out.println("上报失败：" + e.getMessage());
            EventError fbError = new EventError();
            fbError.setErrorPackage(packageId);
            fbError.setErrorMessage("上报失败："+e.getMessage());
            fbError.setErrorContent(event.toString());
            eventErrorMapper.insertEventError(fbError);
            if(publicOwnership!=null)publicOwnership.fromPackage("pkg_event_error",fbError.getId(),fbError.getErrorPackage());
        }
    }


    private void fbLog(String packageID, EvnetHistory fbEvent, Map<String, String> params) {
        EventConfig packageId1 = eventConfigMapper.selectEventConfigByPackageId(packageID);
        if (packageId1 == null) {
            EventError fbError = new EventError();
            fbError.setErrorPackage(packageID);
            fbError.setErrorMessage("包不存在");
            eventErrorMapper.insertEventError(fbError);
            if(publicOwnership!=null)publicOwnership.fromPackage("pkg_event_error",fbError.getId(),fbError.getErrorPackage());
            return;
        }
        try {
            String response = facebook.send(packageId1, fbEvent);
            System.out.println("fb上报成功" + String.format("Standard API response : %s ", response));
            EventError fbError = new EventError();
            fbError.setErrorPackage(packageID);
            fbError.setErrorMessage("fb上报日志："+response.toString());
            fbError.setErrorContent(fbEvent.toString());
            eventErrorMapper.insertEventError(fbError);
            if(publicOwnership!=null)publicOwnership.fromPackage("pkg_event_error",fbError.getId(),fbError.getErrorPackage());
        } catch (RuntimeException e) {
            System.out.println("fb上报失败：" + e.getMessage());
            EventError fbError = new EventError();
            fbError.setErrorPackage(packageID);
            fbError.setErrorMessage("fb上报失败："+e.getMessage());
            fbError.setErrorContent(fbEvent.toString());
            eventErrorMapper.insertEventError(fbError);
            if(publicOwnership!=null)publicOwnership.fromPackage("pkg_event_error",fbError.getId(),fbError.getErrorPackage());
        }
    }
}
