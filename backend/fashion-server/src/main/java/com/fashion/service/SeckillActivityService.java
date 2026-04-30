package com.fashion.service;

import com.fashion.entity.SeckillActivity;
import com.fashion.entity.PageResult;
import java.util.List;
import java.util.Map;

public interface SeckillActivityService {
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
     * 分页查询秒杀活动
     * @param page 页码
     * @param pageSize 每页大小
     * @param name 活动名称
     * @return 分页后的秒杀活动列表
     */
    PageResult<SeckillActivity> pageActivities(int page, int pageSize, String name);
    
    /**
     * 根据ID查询秒杀活动
     * @param id 活动ID
     * @return 秒杀活动
     */
    SeckillActivity getById(Long id);
    
    /**
     * 新增秒杀活动
     * @param seckillActivity 秒杀活动信息
     * @return 是否成功
     */
    boolean save(SeckillActivity seckillActivity);
    
    /**
     * 更新秒杀活动
     * @param seckillActivity 秒杀活动信息
     * @return 是否成功
     */
    boolean update(SeckillActivity seckillActivity);
    
    /**
     * 删除秒杀活动
     * @param id 活动ID
     * @return 是否成功
     */
    boolean removeById(Long id);
}
