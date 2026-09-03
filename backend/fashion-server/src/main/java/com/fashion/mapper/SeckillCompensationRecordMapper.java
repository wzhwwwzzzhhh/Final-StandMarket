package com.fashion.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.fashion.entity.SeckillCompensationRecord;
import java.util.List;

@Mapper
public interface SeckillCompensationRecordMapper {
    int upsertReleaseReservation(@Param("orderNumber") String orderNumber,
                                 @Param("userId") Long userId,
                                 @Param("couponId") Long couponId,
                                 @Param("reason") String reason,
                                 @Param("evidenceMask") long evidenceMask);

    int markRollbackResultOwned(@Param("id") Long id,
                                @Param("worker") String worker,
                                @Param("status") String status,
                                @Param("lastError") String lastError);

    SeckillCompensationRecord selectByOrderNumber(@Param("orderNumber") String orderNumber);

    int markIdentityConflict(@Param("orderNumber") String orderNumber,
                             @Param("userId") Long userId,
                             @Param("couponId") Long couponId);

    int markManualRequiredOwned(@Param("id") Long id,
                                @Param("worker") String worker,
                                @Param("lastError") String lastError);

    List<SeckillCompensationRecord> selectRecoverable(@Param("limit") int limit);

    int claim(@Param("id") Long id, @Param("worker") String worker);

    int claimByOrder(@Param("orderNumber") String orderNumber,
                     @Param("worker") String worker);

    int markExhausted(@Param("maxAttempts") int maxAttempts);

    int hasCompletedCancellationEvidence(@Param("orderNumber") String orderNumber);
}
