package com.fashion.product;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "fashion.product-cache")
public class ProductCacheProperties {

    private Duration listPhysicalTtl = Duration.ofMinutes(15);
    private Duration detailLogicalTtl = Duration.ofMinutes(10);
    private Duration detailPhysicalTtl = Duration.ofMinutes(30);
    private Duration emptyPhysicalTtl = Duration.ofSeconds(30);
    private Duration actualJitter = Duration.ofMinutes(2);
    private Duration emptyJitter = Duration.ofSeconds(10);
    private Duration lockTtl = Duration.ofSeconds(10);

    @PostConstruct
    public void validate() {
        requirePositive("listPhysicalTtl", listPhysicalTtl);
        requirePositive("detailLogicalTtl", detailLogicalTtl);
        requirePositive("detailPhysicalTtl", detailPhysicalTtl);
        requirePositive("emptyPhysicalTtl", emptyPhysicalTtl);
        requireNonNegative("actualJitter", actualJitter);
        requireNonNegative("emptyJitter", emptyJitter);
        requirePositive("lockTtl", lockTtl);
        if (detailPhysicalTtl.compareTo(detailLogicalTtl.plus(lockTtl)) <= 0) {
            throw new IllegalArgumentException("detailPhysicalTtl must exceed detailLogicalTtl plus lockTtl");
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(String name, Duration value) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    public Duration getListPhysicalTtl() { return listPhysicalTtl; }
    public void setListPhysicalTtl(Duration value) { this.listPhysicalTtl = value; }
    public Duration getDetailLogicalTtl() { return detailLogicalTtl; }
    public void setDetailLogicalTtl(Duration value) { this.detailLogicalTtl = value; }
    public Duration getDetailPhysicalTtl() { return detailPhysicalTtl; }
    public void setDetailPhysicalTtl(Duration value) { this.detailPhysicalTtl = value; }
    public Duration getEmptyPhysicalTtl() { return emptyPhysicalTtl; }
    public void setEmptyPhysicalTtl(Duration value) { this.emptyPhysicalTtl = value; }
    public Duration getActualJitter() { return actualJitter; }
    public void setActualJitter(Duration value) { this.actualJitter = value; }
    public Duration getEmptyJitter() { return emptyJitter; }
    public void setEmptyJitter(Duration value) { this.emptyJitter = value; }
    public Duration getLockTtl() { return lockTtl; }
    public void setLockTtl(Duration value) { this.lockTtl = value; }
}
