package com.fashion.mapper;

import com.fashion.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SeckillActivityMapper {
    /**
     * 条件查询
     * @param params 查询参数
     * @return 秒杀活动列表
     */
    List<SeckillActivity> selectByCondition(Map<String, Object> params);
    
    /**
     * 根据名称查询秒杀活动列表
     * @param name 活动名称
     * @return 秒杀活动列表
     */
    List<SeckillActivity> listActivities(String name);
    
    /**
     * 根据ID查询秒杀活动
     * @param id 活动ID
     * @return 秒杀活动
     */
    SeckillActivity getById(Long id);
    
    /**
     * 新增秒杀活动
     * @param seckillActivity 秒杀活动信息
     * @return 影响行数
     */
    int save(SeckillActivity seckillActivity);
    
    /**
     * 更新秒杀活动
     * @param seckillActivity 秒杀活动信息
     * @return 影响行数
     */
    int update(SeckillActivity seckillActivity);
    
    /**
     * 删除秒杀活动
     * @param id 活动ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 根据ID查询秒杀活动
     * @param activityId 活动ID
     * @return 秒杀活动
     */
    @Select("SELECT * FROM seckill_activity WHERE id = #{activityId}")
    SeckillActivity selectById(Long activityId);
}
