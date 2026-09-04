package com.fashion.mapper;

import com.fashion.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户优惠券Mapper（含锁券/核销/释放/过期）
 */
@Mapper
public interface UserCouponMapper {

    /**
     * 使用锁后数据库时间和锁定模板版本领取持有券。
     */
    int insertClaim(@Param("userId") Long userId, @Param("templateId") Long templateId,
                    @Param("eligibilityTime") LocalDateTime eligibilityTime);

    /**
     * 根据id查询（联表带模板信息）
     */
    UserCoupon selectById(Long id);

    /**
     * 事务内读取持有券当前版本并持有排他锁。
     */
    UserCoupon selectByIdForUpdate(Long id);

    /**
     * 在所有资格行锁取得后读取数据库时间线性化点。
     */
    LocalDateTime selectDatabaseTime();

    /**
     * 依据锁定快照和数据库时间执行最终状态 CAS。
     */
    int lockCouponAt(@Param("id") Long id, @Param("userId") Long userId,
                     @Param("templateId") Long templateId,
                     @Param("eligibilityTime") LocalDateTime eligibilityTime);

    /**
     * 查询用户卡包（可按状态过滤，联表带模板信息）
     */
    List<UserCoupon> listByUserId(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 查询用户已领取某模板的数量（每人限领校验）
     */
    int countByUserAndTemplate(@Param("userId") Long userId, @Param("templateId") Long templateId);

    /**
     * 查询某模板已被领取的数量（发行总量校验）
     */
    int countByTemplate(@Param("templateId") Long templateId);

    /**
     * 回填锁定券的核销订单id
     */
    int setUseOrderId(@Param("id") Long id, @Param("userId") Long userId,
                      @Param("orderId") Long orderId);

    /**
     * 核销券（仅锁定中可核销，按订单幂等）
     */
    int useCoupon(@Param("orderId") Long orderId, @Param("userId") Long userId);

    /**
     * 释放券（仅锁定中可释放，按订单幂等）
     */
    int releaseCoupon(@Param("orderId") Long orderId, @Param("userId") Long userId);

    /**
     * 批量置过期（定时任务/懒更新）
     */
    int markExpired();

    /**
     * 查询用户未使用且未过期的券（结算页可用券候选，范围/门槛由服务层过滤）
     */
    List<UserCoupon> listUsable(@Param("userId") Long userId);

    /**
     * 管理端分页查询用户持券（可过滤状态）
     */
    List<UserCoupon> adminPage(@Param("page") int page, @Param("pageSize") int pageSize,
                               @Param("status") Integer status, @Param("keyword") String keyword);

    /**
     * 管理端持券总数
     */
    int adminCount(@Param("status") Integer status, @Param("keyword") String keyword);
}
