package com.fashion.mapper;

import com.fashion.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillReconciliationCandidateMapper {
    List<Long> selectCouponIdsAfter(@Param("afterCouponId") long afterCouponId,
                                    @Param("limit") int limit);

    List<SeckillOrder> selectOrderRowsAfter(@Param("afterOrderId") long afterOrderId,
                                            @Param("limit") int limit);
}
