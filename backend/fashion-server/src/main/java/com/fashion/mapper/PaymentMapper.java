package com.fashion.mapper;

import com.fashion.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

    int insert(Payment payment);

    Payment getByPayNo(@Param("payNo") String payNo);

    Payment getByPayNoAndUserId(@Param("payNo") String payNo, @Param("userId") Long userId);

    Payment getByIdForUpdate(@Param("id") Long id);

    Payment getByOrderIdAndType(@Param("orderId") Long orderId, @Param("orderType") Integer orderType);

    Payment getActiveByOrderIdAndType(@Param("orderId") Long orderId,
                                      @Param("orderType") Integer orderType);

    Payment getActiveByOrderIdAndTypeForUpdate(@Param("orderId") Long orderId,
                                               @Param("orderType") Integer orderType);

    int markSuccess(@Param("id") Long id,
                    @Param("tradeNo") String tradeNo,
                    @Param("payTime") java.time.LocalDateTime payTime);
}
