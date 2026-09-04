package com.fashion.product;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProductCacheTtlPolicyTest {

    @Test
    void defaultDurationsHaveExplicitUnitsAndEveryValueHasPhysicalTtl() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.validate();

        assertThat(properties.getListPhysicalTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.getDetailLogicalTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getDetailPhysicalTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getEmptyPhysicalTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getLockTtl()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void deterministicJitterCoversZeroAndInclusiveMaximum() {
        ProductCacheTtlPolicy zero = new ProductCacheTtlPolicy(() -> 0L);
        ProductCacheTtlPolicy max = new ProductCacheTtlPolicy(() -> 120_000L);

        assertThat(zero.withJitter(Duration.ofMinutes(15), Duration.ofMinutes(2)))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(max.withJitter(Duration.ofMinutes(15), Duration.ofMinutes(2)))
                .isEqualTo(Duration.ofMinutes(17));
    }

    @Test
    void rejectsInvalidDurationsAndOutOfRangeJitterSource() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setDetailPhysicalTtl(Duration.ofMinutes(10));
        assertThatIllegalArgumentException().isThrownBy(properties::validate);

        ProductCacheTtlPolicy policy = new ProductCacheTtlPolicy(() -> 11L);
        assertThatIllegalArgumentException().isThrownBy(
                () -> policy.withJitter(Duration.ofSeconds(1), Duration.ofMillis(10)));
    }
}
