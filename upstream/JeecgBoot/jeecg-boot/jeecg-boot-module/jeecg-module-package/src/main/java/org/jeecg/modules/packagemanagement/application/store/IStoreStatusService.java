package org.jeecg.modules.packagemanagement.application.store;

import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;

import java.util.List;

/**
 * 包状态Service接口
 *
 * @author ruoyi
 * @date 2023-11-04
 */
public interface IStoreStatusService
{
    /**
     * 查询包状态
     *
     * @param id
     * @return
     */
    public StoreStatus selectStoreStatusById(Long id);

    /**
     * 查询包状态列表
     *
     * @param storeStatus 包状态
     * @return 包状态集合
     */
    public List<StoreStatus> selectStoreStatusList(StoreStatus storeStatus);

    /**
     * 新增包统计
     *
     * @param storeStatus 包状态
     * @return 结果
     */
    public int insertStoreStatus(StoreStatus storeStatus);

    /**
     * 修改包统计
     *
     * @param storeStatus 包状态
     * @return 结果
     */
    public int updateStoreStatusAndDate(StoreStatus storeStatus);


    /**
     * 批量删除包状态
     *
     * @param ids 需要删除的包状态主键集合
     * @return 结果
     */
    public int deleteStoreStatusByIds(Long[] ids);

    /**
     * 删除包状态信息
     *
     * @param id 包状态主键
     * @return 结果
     */
    public int deleteStoreStatusById(Long id);

    public int updateStoreStatus(StoreStatus storeStatus);

    public  void getStatus();

    /** Probe one platform from application management: {@code android} or {@code ios}. */
    public void getStatus(String platform);
}
