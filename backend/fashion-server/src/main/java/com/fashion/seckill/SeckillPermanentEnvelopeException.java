package com.fashion.seckill;

/** Permanent contract/identity failure that must be quarantined, never business-retried. */
public class SeckillPermanentEnvelopeException extends RuntimeException {
    public SeckillPermanentEnvelopeException(String message) {
        super(message);
    }
}
