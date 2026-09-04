package com.fashion.service;

import com.fashion.entity.CouponTemplate;
import com.fashion.entity.PageResult;
import com.fashion.entity.UserCoupon;
import com.fashion.vo.AvailableCouponVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 通用优惠券服务（领券 / 查券 / 锁券 / 核销 / 释放）
 */
public interface CouponService {

    // ==================== 管理端 ====================

    /**
     * 新增模板
     */
    void saveTemplate(CouponTemplate template);

    /**
     * 更新模板
     */
    void updateTemplate(CouponTemplate template);

    /**
     * 删除模板（软删）
     */
    void deleteTemplate(Long id);

    /**
     * 模板详情
     */
    CouponTemplate getTemplate(Long id);

    /**
     * 分页查询模板
     */
    PageResult<CouponTemplate> pageTemplates(int page, int pageSize, String name, Integer status);

    /**
     * 分页查询用户持券（运营管理）
     */
    PageResult<UserCoupon> pageUserCoupons(int page, int pageSize, Integer status, String keyword);

    // ==================== 用户端 ====================

    /**
     * 可领取模板列表
     */
    List<CouponTemplate> listClaimableTemplates();

    /**
     * 领取优惠券
     */
    void claim(Long userId, Long templateId);

    /**
     * 我的卡包
     */
    List<UserCoupon> listMyCoupons(Long userId, Integer status);

    /**
     * 结算页可用券（按金额门槛 + 商品范围过滤）
     */
    List<AvailableCouponVO> listAvailable(Long userId, List<Long> cartItemIds);

    // ==================== 下单集成（供 OrderServiceImpl 调用） ====================

    /**
     * 锁券并计算抵扣金额：校验归属/状态/有效期/门槛/适用范围后乐观锁置为 status=3
     *
     * @return 抵扣金额（已按订单金额封顶）
     */
    BigDecimal lockAndDiscount(Long userId, Long userCouponId, BigDecimal totalAmount, List<Long> productIds);

    /**
     * 下单成功后把券绑定到订单（回填 use_order_id，便于按订单幂等核销/释放）
     */
    void bindUseOrder(Long userId, Long userCouponId, Long orderId);

    /**
     * 支付成功核销券（幂等：仅锁定中、绑定该订单的券）
     */
    void markUsed(Long userId, Long orderId);

    /**
     * 订单取消/支付失败释放券（幂等）
     */
    void release(Long userId, Long orderId);

    /**
     * 批量置过期（懒处理/定时任务）
     */
    void markExpired();
}
