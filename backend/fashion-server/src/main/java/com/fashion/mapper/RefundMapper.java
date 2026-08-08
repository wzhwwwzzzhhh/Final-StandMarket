package com.fashion.mapper;

import com.fashion.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 退款Mapper
 */
@Mapper
public interface RefundMapper {

    /**
     * 插入退款记录
     */
    int insert(Refund refund);

    /**
     * 根据ID查询
     */
    Refund getById(Long id);

    /**
     * 根据退款单号查询
     */
    Refund getByRefundNo(String refundNo);

    /**
     * 查询用户的退款列表
     */
    List<Refund> listByUserId(@Param("userId") Long userId);

    /**
     * 管理端查询所有退款列表（可筛选状态）
     */
    List<Refund> listAll(@Param("status") Integer status);

    /**
     * 根据订单ID和状态查询退款记录
     */
    List<Refund> listByOrderIdAndStatus(@Param("orderId") Long orderId, @Param("status") Integer status);

    /**
     * 更新退款记录
     */
    int update(Refund refund);
}
