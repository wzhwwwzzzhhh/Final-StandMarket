package com.fashion.mapper;

import com.fashion.entity.SeckillMessageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillMessageLogMapper {
    int insert(SeckillMessageLog messageLog);

    int insertIfAbsent(SeckillMessageLog messageLog);

    SeckillMessageLog selectByMessageId(@Param("messageId") String messageId);

    SeckillMessageLog selectByMessageIdForUpdate(@Param("messageId") String messageId);

    int claimNextPublishAttempt(@Param("messageId") String messageId,
                                @Param("publishPurpose") String publishPurpose);

    int markSynchronousFailure(@Param("messageId") String messageId,
                               @Param("correlationId") String correlationId,
                               @Param("lastError") String lastError);

    int recordReturn(@Param("messageId") String messageId,
                     @Param("correlationId") String correlationId,
                     @Param("replyCode") Integer replyCode,
                     @Param("replyText") String replyText,
                     @Param("exchange") String exchange,
                     @Param("routingKey") String routingKey);

    int applyCallbackAction(@Param("messageId") String messageId,
                            @Param("correlationId") String correlationId,
                            @Param("action") String action,
                            @Param("lastError") String lastError);

    int appendCallbackAudit(@Param("messageId") String messageId,
                            @Param("correlationId") String correlationId,
                            @Param("summary") String summary);

    int claimConsumeAttempt(@Param("messageId") String messageId,
                            @Param("incomingAttempt") int incomingAttempt,
                            @Param("businessKey") String businessKey,
                            @Param("userId") Long userId,
                            @Param("couponId") Long couponId,
                            @Param("worker") String worker);

    int markConsumedAttempt(@Param("messageId") String messageId,
                            @Param("incomingAttempt") int incomingAttempt,
                            @Param("worker") String worker);

    int claimTimeoutConsumeAttempt(@Param("messageId") String messageId,
                                   @Param("incomingAttempt") int incomingAttempt,
                                   @Param("businessKey") String businessKey,
                                   @Param("payload") String payload,
                                   @Param("worker") String worker);

    int markTimeoutConsumedAttempt(@Param("messageId") String messageId,
                                   @Param("incomingAttempt") int incomingAttempt,
                                   @Param("worker") String worker);

    int markTimeoutFallbackConsumed(@Param("messageId") String messageId);

    int recordTimeoutFallbackFailure(@Param("messageId") String messageId,
                                     @Param("lastError") String lastError,
                                     @Param("maxAttempts") int maxAttempts);

    int markInitialCompensated(@Param("orderNumber") String orderNumber);

    int recordConsumeFailure(@Param("messageId") String messageId,
                             @Param("incomingAttempt") int incomingAttempt,
                             @Param("lastError") String lastError);

    int recordTimeoutConsumeFailure(@Param("messageId") String messageId,
                                    @Param("incomingAttempt") int incomingAttempt,
                                    @Param("lastError") String lastError,
                                    @Param("claimToken") String claimToken);

    int markConsumeAttemptGap(@Param("messageId") String messageId,
                              @Param("incomingAttempt") int incomingAttempt,
                              @Param("lastError") String lastError);

    int updateSourceDeadLetterStatus(@Param("messageId") String messageId,
                                     @Param("deadLetterStatus") String deadLetterStatus);

    int markConfirmTimeouts();

    int releaseExpiredTimeoutClaims();

    int markPublishAttemptsExhausted(@Param("maxAttempts") int maxAttempts);

    int markSourcesWithExhaustedDeadLetters();

    int scheduleReconciliationRedelivery(@Param("messageId") String messageId,
                                         @Param("maxAttempts") int maxAttempts);

    List<SeckillMessageLog> selectInitialCompensationPending(@Param("limit") int limit);

    List<SeckillMessageLog> selectConsumeExhaustedWithoutDeadLetter(@Param("limit") int limit);

    List<SeckillMessageLog> selectRecoverable(@Param("limit") int limit);
}
