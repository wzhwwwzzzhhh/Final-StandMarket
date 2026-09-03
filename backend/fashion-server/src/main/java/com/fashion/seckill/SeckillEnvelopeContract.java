package com.fashion.seckill;

import org.springframework.amqp.core.Message;
import org.springframework.util.MimeTypeUtils;

import java.util.Map;
import java.util.Objects;
import java.math.BigDecimal;

final class SeckillEnvelopeContract {
    private SeckillEnvelopeContract() { }

    static int require(Message envelope, String expectedType, String expectedBusinessKey) {
        String contentType = envelope.getMessageProperties().getContentType();
        Map<String, Object> headers = envelope.getMessageProperties().getHeaders();
        Object schema = headers.get("fsm-schema-version");
        Object publishAttempt = headers.get("fsm-publish-attempt");
        Object consumeAttempt = headers.get("fsm-consume-attempt");
        if (!MimeTypeUtils.APPLICATION_JSON_VALUE.equalsIgnoreCase(contentType)
                || !Objects.equals(expectedType, headers.get("fsm-message-type"))
                || !Objects.equals(expectedBusinessKey, String.valueOf(headers.get("fsm-business-key")))
                || !exactIntegerBetween(schema, 1, 1)
                || !exactIntegerBetween(publishAttempt, 1, 5)
                || !exactIntegerBetween(consumeAttempt, 1, 3)) {
            throw new IllegalArgumentException("invalid B6 envelope contract");
        }
        return new BigDecimal(consumeAttempt.toString()).intValueExact();
    }

    private static boolean exactIntegerBetween(Object value, int minimum, int maximum) {
        if (!(value instanceof Number)) return false;
        try {
            int exact = new BigDecimal(value.toString()).intValueExact();
            return exact >= minimum && exact <= maximum;
        } catch (ArithmeticException | NumberFormatException invalidNumber) {
            return false;
        }
    }
}
