package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;

import java.util.List;

/**
 *
 * @author ruoyi
 * @date 2023-11-04
 */
public interface StoreStatusMapper
{
    /**
     *
     * @param id
     * @return
     */
    public StoreStatus selectStoreStatusById(Long id);

    /**
     *
     * @param storeStatus
     * @return 包状态集合
     */
    public List<StoreStatus> selectStoreStatusList(StoreStatus storeStatus);


    /**
     * 新增包状态
     *
     * @param storeStatus 包状态
     * @return 结果
     */
    public int insertStoreStatus(StoreStatus storeStatus);

    /**
     * 查询包名查询storeStatus 主要用于检测包名是否重复
     * @param packageName
     * @return
     */
    public StoreStatus selectStoreStatusByPackageName(String  packageName);


    /**
     * 修改包状态
     *
     * @param storeStatus 包状态
     * @return 结果
     */
    public int updateStoreStatusAndDate(StoreStatus storeStatus);

    /**
     * 删除包状态
     *
     * @param id 包状态主键
     * @return 结果
     */
    public int deleteStoreStatusById(Long id);


    public int updateStoreStatus(StoreStatus storeStatus);

    /**
     * 批量删除包状态
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteStoreStatusByIds(Long[] ids);
}
