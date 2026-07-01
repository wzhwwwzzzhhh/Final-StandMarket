package com.fashion.mapper;

import com.fashion.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

    int insert(Payment payment);

    Payment getByPayNo(@Param("payNo") String payNo);

    Payment getByOrderId(@Param("orderId") Long orderId);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updatePayTime(@Param("id") Long id, @Param("payTime") java.time.LocalDateTime payTime);
}
