package com.fashion.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.fashion.entity.OrderDetail;
import java.util.List;

/**
 * 订单明细Mapper
 */
@Mapper
public interface OrderDetailMapper {
    /**
     * 根据订单ID查询订单明细
     * @param orderId 订单ID
     * @return 订单明细列表
     */
    List<OrderDetail> listByOrderId(@Param("orderId") Long orderId);

    /**
     * 批量插入订单明细
     * @param orderDetails 订单明细列表
     * @return 影响行数
     */
    int batchInsert(@Param("orderDetails") List<OrderDetail> orderDetails);

    /**
     * 插入订单明细
     * @param orderDetail 订单明细
     */
    void insert(OrderDetail orderDetail);
}
