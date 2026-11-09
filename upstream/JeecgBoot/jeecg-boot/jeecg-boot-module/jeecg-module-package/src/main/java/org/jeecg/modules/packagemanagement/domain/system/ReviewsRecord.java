package org.jeecg.modules.packagemanagement.domain.system;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;

/**
 * 【请填写功能名称】对象 reviews_record
 * 
 * @author ruoyi
 * @date 2024-10-15
 */
public class ReviewsRecord extends PackageEntity
{
    private static final long serialVersionUID = 1L;

    /** 回复id */
    private Long reviewsId;
    private Long appId;

    /** 头像 */

    private String headPortraitUrl;

    /** 名字 */

    private String name;

    /** 内容 */

    private String content;

    /** 评分 */

    private Long score;

    public void setReviewsId(Long reviewsId) 
    {
        this.reviewsId = reviewsId;
    }

    public Long getReviewsId() 
    {
        return reviewsId;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public void setHeadPortraitUrl(String headPortraitUrl)
    {
        this.headPortraitUrl = headPortraitUrl;
    }

    public String getHeadPortraitUrl() 
    {
        return headPortraitUrl;
    }
    public void setName(String name) 
    {
        this.name = name;
    }

    public String getName() 
    {
        return name;
    }
    public void setContent(String content) 
    {
        this.content = content;
    }

    public String getContent() 
    {
        return content;
    }
    public void setScore(Long score) 
    {
        this.score = score;
    }

    public Long getScore() 
    {
        return score;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("reviewsId", getReviewsId())
            .append("headPortraitUrl", getHeadPortraitUrl())
            .append("name", getName())
            .append("content", getContent())
            .append("score", getScore())
            .append("createTime", getCreateTime())
            .toString();
    }
}
