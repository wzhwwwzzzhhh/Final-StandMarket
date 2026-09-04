package com.fashion.product;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "fashion.product-projection")
public class ProductProjectionProperties {
    private int maxAttempts = 8;
    private int batchSize = 20;
    private int reconcileBatchSize = 4;
    private Duration lease = Duration.ofSeconds(30);
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration socketTimeout = Duration.ofSeconds(3);
    private Duration connectionRequestTimeout = Duration.ofSeconds(1);
    private Duration leaseMargin = Duration.ofSeconds(3);
    private Duration retryBase = Duration.ofSeconds(1);
    private Duration retryMax = Duration.ofMinutes(5);
    private Duration retryJitter = Duration.ofSeconds(1);
    private String indexName = "products";

    @PostConstruct
    public void validate() {
        if (maxAttempts < 1 || maxAttempts > 8) throw new IllegalArgumentException("maxAttempts must be 1..8");
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("batchSize must be 1..100");
        if (reconcileBatchSize < 1 || reconcileBatchSize > 20) {
            throw new IllegalArgumentException("reconcileBatchSize must be 1..20");
        }
        positive("lease", lease);
        positive("connectTimeout", connectTimeout);
        positive("socketTimeout", socketTimeout);
        positive("connectionRequestTimeout", connectionRequestTimeout);
        if (leaseMargin == null || leaseMargin.isNegative()) {
            throw new IllegalArgumentException("leaseMargin must not be negative");
        }
        Duration requestBudget = requestTimeoutBudget();
        requireDeliveryWindow(requestBudget);
        long calls = (long) reconcileBatchSize + 1L;
        Duration reconciliationWindow;
        try {
            reconciliationWindow = requestBudget.multipliedBy(calls);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("reconciliation batch timeout exceeds lease", overflow);
        }
        if (reconciliationWindow.plus(leaseMargin).compareTo(lease) >= 0) {
            throw new IllegalArgumentException("reconciliation batch timeout plus margin must be less than lease");
        }
        positive("retryBase", retryBase);
        positive("retryMax", retryMax);
        if (retryMax.compareTo(retryBase) < 0) throw new IllegalArgumentException("retryMax must be >= retryBase");
        if (retryJitter == null || retryJitter.isNegative()) throw new IllegalArgumentException("retryJitter must not be negative");
        if (indexName == null || !indexName.matches("[a-z0-9][a-z0-9_-]{0,127}")) {
            throw new IllegalArgumentException("invalid product projection index name");
        }
    }

    public void requireDeliveryWindow(Duration duration) {
        positive("deliveryWindow", duration);
        if (duration.plus(leaseMargin).compareTo(lease) >= 0) {
            throw new IllegalArgumentException("delivery timeout plus margin must be less than lease");
        }
    }

    public Duration requestTimeoutBudget() {
        return connectTimeout.plus(socketTimeout).plus(connectionRequestTimeout);
    }

    private void positive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { this.maxAttempts = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { this.batchSize = value; }
    public int getReconcileBatchSize() { return reconcileBatchSize; }
    public void setReconcileBatchSize(int value) { this.reconcileBatchSize = value; }
    public Duration getLease() { return lease; }
    public void setLease(Duration value) { this.lease = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { this.connectTimeout = value; }
    public Duration getSocketTimeout() { return socketTimeout; }
    public void setSocketTimeout(Duration value) { this.socketTimeout = value; }
    public Duration getConnectionRequestTimeout() { return connectionRequestTimeout; }
    public void setConnectionRequestTimeout(Duration value) { this.connectionRequestTimeout = value; }
    public Duration getLeaseMargin() { return leaseMargin; }
    public void setLeaseMargin(Duration value) { this.leaseMargin = value; }
    public Duration getRetryBase() { return retryBase; }
    public void setRetryBase(Duration value) { this.retryBase = value; }
    public Duration getRetryMax() { return retryMax; }
    public void setRetryMax(Duration value) { this.retryMax = value; }
    public Duration getRetryJitter() { return retryJitter; }
    public void setRetryJitter(Duration value) { this.retryJitter = value; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String value) { this.indexName = value; }
}
