package com.fashion.product;

public final class ProjectionDeliveryException extends RuntimeException {
    private final boolean retryable;
    private final String safeSummary;

    private ProjectionDeliveryException(boolean retryable, String safeSummary, Throwable cause) {
        super(sanitize(safeSummary), cause);
        this.retryable = retryable;
        this.safeSummary = sanitize(safeSummary);
    }

    public static ProjectionDeliveryException retryable(String safeSummary) {
        return new ProjectionDeliveryException(true, safeSummary, null);
    }

    public static ProjectionDeliveryException retryable(String safeSummary, Throwable cause) {
        return new ProjectionDeliveryException(true, safeSummary, cause);
    }

    public static ProjectionDeliveryException permanent(String safeSummary) {
        return new ProjectionDeliveryException(false, safeSummary, null);
    }

    public static ProjectionDeliveryException permanent(String safeSummary, Throwable cause) {
        return new ProjectionDeliveryException(false, safeSummary, cause);
    }

    public boolean isRetryable() { return retryable; }
    public String getSafeSummary() { return safeSummary; }

    private static String sanitize(String value) {
        String safe = value == null ? "projection_delivery_failed"
                : value.replace('\r', ' ').replace('\n', ' ');
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
