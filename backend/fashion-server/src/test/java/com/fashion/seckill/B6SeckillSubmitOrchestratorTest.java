package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.utils.UniqueID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.AmqpConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@DisplayName("B6 秒杀提交可靠编排")
class B6SeckillSubmitOrchestratorTest {
    private UniqueID uniqueID;
    private SeckillReservationService reservationService;
    private SeckillMessagePrepareTransaction prepareTransaction;
    private SeckillReliablePublisher publisher;
    private SeckillCompensationService compensationService;
    private SeckillCompensationExecutor compensationExecutor;
    private SeckillSubmitOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        uniqueID = mock(UniqueID.class);
        reservationService = mock(SeckillReservationService.class);
        prepareTransaction = mock(SeckillMessagePrepareTransaction.class);
        publisher = mock(SeckillReliablePublisher.class);
        compensationService = mock(SeckillCompensationService.class);
        compensationExecutor = mock(SeckillCompensationExecutor.class);
        orchestrator = new SeckillSubmitOrchestrator(uniqueID, reservationService,
                prepareTransaction, publisher, compensationService, compensationExecutor);
        when(uniqueID.nextId("seckill:order")).thenReturn(9001L);
        when(reservationService.reserve(19L, 7L, "9001", 1725148800L))
                .thenReturn(SeckillReservationService.ReserveResult.RESERVED);
        SeckillMessageLog log = new SeckillMessageLog();
        log.setMessageId("SECKILL_ORDER_CREATE:9001");
        when(prepareTransaction.prepareOrderCreate(eqOrder(), eqUser(), eqCoupon(), anyString()))
                .thenReturn(log);
    }

    @Test
    @DisplayName("订单号在 Redis reservation 前生成并用于 PREPARED 与 publish")
    void orderTokenExistsBeforeReservation() {
        SeckillSubmitOrchestrator.Submission result = orchestrator.submit(
                7L, 19L, 1725148800L);

        assertEquals(SeckillSubmitOrchestrator.Outcome.PROCESSING, result.getOutcome());
        assertEquals("9001", result.getOrderNumber());
        InOrder order = inOrder(uniqueID, reservationService, prepareTransaction, publisher);
        order.verify(uniqueID).nextId("seckill:order");
        order.verify(reservationService).reserve(19L, 7L, "9001", 1725148800L);
        order.verify(prepareTransaction).prepareOrderCreate(eqOrder(), eqUser(), eqCoupon(), anyString());
        order.verify(publisher).publish("SECKILL_ORDER_CREATE:9001", "INITIAL");
    }

    @Test
    @DisplayName("同步发送异常登记补偿并立即恢复 Redis")
    void synchronousPublishFailureCompensatesImmediately() {
        when(publisher.publish("SECKILL_ORDER_CREATE:9001", "INITIAL"))
                .thenThrow(new AmqpConnectException(new IllegalStateException("down")));
        when(compensationExecutor.execute("9001"))
                .thenReturn(SeckillReservationService.RollbackResult.APPLIED);

        SeckillSubmitOrchestrator.Submission result = orchestrator.submit(
                7L, 19L, 1725148800L);

        assertEquals(SeckillSubmitOrchestrator.Outcome.DELIVERY_FAILED, result.getOutcome());
        verify(compensationService).requestRelease("9001", 7L, 19L,
                "INITIAL_DELIVERY_FAILED", SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED);
        verify(compensationExecutor).execute("9001");
    }

    @Test
    @DisplayName("补偿事实暂时无法写入时仍先立即执行 token-aware Redis 回补")
    void compensationPersistenceFailureDoesNotSkipImmediateRollback() {
        when(publisher.publish("SECKILL_ORDER_CREATE:9001", "INITIAL"))
                .thenThrow(new AmqpConnectException(new IllegalStateException("down")));
        doThrow(new IllegalStateException("mysql unavailable")).when(compensationService)
                .requestRelease("9001", 7L, 19L, "INITIAL_DELIVERY_FAILED",
                        SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED);
        when(reservationService.rollback(19L, 7L, "9001"))
                .thenReturn(SeckillReservationService.RollbackResult.APPLIED);

        assertThrows(IllegalStateException.class,
                () -> orchestrator.submit(7L, 19L, 1725148800L));

        verify(reservationService).rollback(19L, 7L, "9001");
    }

    private String eqOrder() { return org.mockito.ArgumentMatchers.eq("9001"); }
    private Long eqUser() { return org.mockito.ArgumentMatchers.eq(7L); }
    private Long eqCoupon() { return org.mockito.ArgumentMatchers.eq(19L); }
}
