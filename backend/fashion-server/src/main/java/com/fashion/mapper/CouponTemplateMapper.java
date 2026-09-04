package com.fashion.mapper;

import com.fashion.entity.CouponTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 优惠券模板Mapper
 */
@Mapper
public interface CouponTemplateMapper {

    /**
     * 新增模板
     */
    int insert(CouponTemplate template);

    /**
     * 更新模板（动态SQL）
     */
    int update(CouponTemplate template);

    /**
     * 删除模板（软删 status=0）
     */
    int deleteById(Long id);

    /**
     * 根据id查询模板
     */
    CouponTemplate selectById(Long id);

    /**
     * 事务内读取当前模板版本并持有共享锁，防止资格判断期间被管理端改写。
     */
    CouponTemplate selectByIdForShare(Long id);

    /**
     * 分页查询模板
     */
    List<CouponTemplate> list(@Param("page") int page, @Param("pageSize") int pageSize,
                              @Param("name") String name, @Param("status") Integer status);

    /**
     * 查询模板总数
     */
    int count(@Param("name") String name, @Param("status") Integer status);

    /**
     * 查询可领取模板列表（用户端领券中心：启用且处于有效期内）
     */
    List<CouponTemplate> listClaimable();
}
