package com.fashion.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeckillReconciliationAnomalyMapper {
    java.time.LocalDateTime selectDatabaseTime();

    int upsert(@Param("anomalyType") String anomalyType,
               @Param("couponId") Long couponId,
               @Param("sampleUserId") Long sampleUserId,
               @Param("sampleOrderNumber") String sampleOrderNumber,
               @Param("detailsHash") String detailsHash);

    int markCleanScan(@Param("couponId") Long couponId,
                      @Param("cycleStartedAt") java.time.LocalDateTime cycleStartedAt);
}
