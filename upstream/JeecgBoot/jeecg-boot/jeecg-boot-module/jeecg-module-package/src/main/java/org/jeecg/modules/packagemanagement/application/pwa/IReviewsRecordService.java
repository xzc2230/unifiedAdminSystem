package org.jeecg.modules.packagemanagement.application.pwa;

import java.util.List;
import org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord;

/**
 * 【请填写功能名称】Service接口
 *
 * @author ruoyi
 * @date 2024-10-15
 */
public interface IReviewsRecordService
{
    /**
     * 查询【请填写功能名称】
     *
     * @param reviewsId 【请填写功能名称】主键
     * @return 【请填写功能名称】
     */
    public ReviewsRecord selectReviewsRecordByReviewsId(Long reviewsId);

    /**
     * 查询【请填写功能名称】列表
     *
     * @param reviewsRecord 【请填写功能名称】
     * @return 【请填写功能名称】集合
     */
    public List<ReviewsRecord> selectReviewsRecordList(ReviewsRecord reviewsRecord);

    /**
     * 新增【请填写功能名称】
     *
     * @param reviewsRecord 【请填写功能名称】
     * @return 结果
     */
    public int insertReviewsRecord(ReviewsRecord reviewsRecord);

    /**
     * 修改【请填写功能名称】
     *
     * @param reviewsRecord 【请填写功能名称】
     * @return 结果
     */
    public int updateReviewsRecord(ReviewsRecord reviewsRecord);

    /**
     * 批量删除【请填写功能名称】
     *
     * @param reviewsIds 需要删除的【请填写功能名称】主键集合
     * @return 结果
     */
    public int deleteReviewsRecordByReviewsIds(Long[] reviewsIds);

    /**
     * 删除【请填写功能名称】信息
     *
     * @param reviewsId 【请填写功能名称】主键
     * @return 结果
     */
    public int deleteReviewsRecordByReviewsId(Long reviewsId);
}
