package com.fashion.seckill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("B6 AMQP envelope 数值契约")
class B6EnvelopeContractTest {
    @Test
    void acceptsExactBoundedAttempts() {
        assertEquals(3, SeckillEnvelopeContract.require(envelope(1, 5, 3), "ORDER_CREATE", "9001"));
    }

    @Test
    void rejectsFractionalAndOverflowingNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> SeckillEnvelopeContract.require(envelope(1.9d, 1, 1), "ORDER_CREATE", "9001"));
        assertThrows(IllegalArgumentException.class,
                () -> SeckillEnvelopeContract.require(envelope(1, 4294967297L, 1), "ORDER_CREATE", "9001"));
    }

    @Test
    void rejectsAttemptsOutsideDatabaseBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> SeckillEnvelopeContract.require(envelope(1, 6, 1), "ORDER_CREATE", "9001"));
        assertThrows(IllegalArgumentException.class,
                () -> SeckillEnvelopeContract.require(envelope(1, 1, 4), "ORDER_CREATE", "9001"));
    }

    private Message envelope(Number schema, Number publishAttempt, Number consumeAttempt) {
        return MessageBuilder.withBody("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setHeader("fsm-message-type", "ORDER_CREATE")
                .setHeader("fsm-business-key", "9001")
                .setHeader("fsm-schema-version", schema)
                .setHeader("fsm-publish-attempt", publishAttempt)
                .setHeader("fsm-consume-attempt", consumeAttempt)
                .build();
    }
}
