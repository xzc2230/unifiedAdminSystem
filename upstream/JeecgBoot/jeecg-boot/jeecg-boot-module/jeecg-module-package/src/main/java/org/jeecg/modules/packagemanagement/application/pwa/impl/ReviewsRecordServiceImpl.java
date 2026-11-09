package org.jeecg.modules.packagemanagement.application.pwa.impl;

import java.util.List;
import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.ReviewsRecordMapper;
import org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord;
import org.jeecg.modules.packagemanagement.application.pwa.IReviewsRecordService;

/**
 * 【请填写功能名称】Service业务层处理
 *
 * @author ruoyi
 * @date 2024-10-15
 */
@Service
public class ReviewsRecordServiceImpl implements IReviewsRecordService
{
    @Autowired
    private ReviewsRecordMapper reviewsRecordMapper;

    /**
     * 查询【请填写功能名称】
     *
     * @param reviewsId 【请填写功能名称】主键
     * @return 【请填写功能名称】
     */
    @Override
    public ReviewsRecord selectReviewsRecordByReviewsId(Long reviewsId)
    {
        return reviewsRecordMapper.selectReviewsRecordByReviewsId(reviewsId);
    }

    /**
     * 查询【请填写功能名称】列表
     *
     * @param reviewsRecord 【请填写功能名称】
     * @return 【请填写功能名称】
     */
    @Override
    public List<ReviewsRecord> selectReviewsRecordList(ReviewsRecord reviewsRecord)
    {
        return reviewsRecordMapper.selectReviewsRecordList(reviewsRecord);
    }

    /**
     * 新增【请填写功能名称】
     *
     * @param reviewsRecord 【请填写功能名称】
     * @return 结果
     */
    @Override
    public int insertReviewsRecord(ReviewsRecord reviewsRecord)
    {
        reviewsRecord.setCreateTime(DateUtils.getNowDate());
        return reviewsRecordMapper.insertReviewsRecord(reviewsRecord);
    }

    /**
     * 修改【请填写功能名称】
     *
     * @param reviewsRecord 【请填写功能名称】
     * @return 结果
     */
    @Override
    public int updateReviewsRecord(ReviewsRecord reviewsRecord)
    {
        return reviewsRecordMapper.updateReviewsRecord(reviewsRecord);
    }

    /**
     * 批量删除【请填写功能名称】
     *
     * @param reviewsIds 需要删除的【请填写功能名称】主键
     * @return 结果
     */
    @Override
    public int deleteReviewsRecordByReviewsIds(Long[] reviewsIds)
    {
        return reviewsRecordMapper.deleteReviewsRecordByReviewsIds(reviewsIds);
    }

    /**
     * 删除【请填写功能名称】信息
     *
     * @param reviewsId 【请填写功能名称】主键
     * @return 结果
     */
    @Override
    public int deleteReviewsRecordByReviewsId(Long reviewsId)
    {
        return reviewsRecordMapper.deleteReviewsRecordByReviewsId(reviewsId);
    }
}
