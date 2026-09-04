package com.fashion.product;

import com.fashion.utils.CacheClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductCatalogVersionGateTest {

    private final ProductCatalogAuthority authority = mock(ProductCatalogAuthority.class);
    private final CacheClient cache = mock(CacheClient.class);
    private final ProductProjectionMetrics metrics = new ProductProjectionMetrics();
    private final ProductCatalogVersionGate gate = new ProductCatalogVersionGate(authority, cache, metrics);

    @Test
    void missingOrBehindMirrorMustBePublishedBeforeCacheCanBeRead() {
        when(authority.readListVersion()).thenReturn(8L);
        when(cache.getRaw(ProductCacheKeys.LIST_PUBLISHED_VERSION)).thenReturn(null);
        when(cache.publishMaxVersion(ProductCacheKeys.LIST_PUBLISHED_VERSION, 8L)).thenReturn(1L);

        VersionDecision missing = gate.listVersion();
        assertThat(missing.getVersion()).isEqualTo(8L);
        assertThat(missing.isCacheAllowed()).isTrue();

        reset(cache);
        when(authority.readListVersion()).thenReturn(8L);
        when(cache.getRaw(ProductCacheKeys.LIST_PUBLISHED_VERSION)).thenReturn("7");
        when(cache.publishMaxVersion(ProductCacheKeys.LIST_PUBLISHED_VERSION, 8L)).thenReturn(1L);
        assertThat(gate.listVersion().isCacheAllowed()).isTrue();
        assertThat(metrics.count("cache.list.mirror_missing")).isEqualTo(1);
        assertThat(metrics.count("cache.list.mirror_behind")).isEqualTo(1);
        assertThat(metrics.count("cache.list.publish_success")).isEqualTo(2);
    }

    @Test
    void redisFailureCorruptionOrFailedPublishBypassesCacheWithoutLosingMysqlFact() {
        when(authority.readListVersion()).thenReturn(9L);
        when(cache.getRaw(ProductCacheKeys.LIST_PUBLISHED_VERSION)).thenThrow(new RuntimeException("redis down"));
        VersionDecision unavailable = gate.listVersion();
        assertThat(unavailable.getVersion()).isEqualTo(9L);
        assertThat(unavailable.isCacheAllowed()).isFalse();

        reset(cache);
        when(cache.getRaw(ProductCacheKeys.LIST_PUBLISHED_VERSION)).thenReturn("not-a-number");
        assertThat(gate.listVersion().isCacheAllowed()).isFalse();
        assertThat(metrics.count("cache.list.bypass_redis_error")).isEqualTo(1);
        assertThat(metrics.count("cache.list.bypass_corrupt")).isEqualTo(1);

        reset(cache);
        when(cache.getRaw(ProductCacheKeys.LIST_PUBLISHED_VERSION)).thenReturn("8");
        when(cache.publishMaxVersion(ProductCacheKeys.LIST_PUBLISHED_VERSION, 9L)).thenReturn(0L);
        assertThat(gate.listVersion().isCacheAllowed()).isFalse();
        assertThat(metrics.count("cache.list.publish_rejected")).isEqualTo(1);
    }

    @Test
    void unexplainedAheadMirrorNeverRaisesMysqlVersionOrReadsCache() {
        when(authority.readListVersion()).thenReturn(10L, 10L);
        when(cache.getRaw(ProductCacheKeys.LIST_PUBLISHED_VERSION)).thenReturn("11");

        VersionDecision decision = gate.listVersion();

        assertThat(decision.getVersion()).isEqualTo(10L);
        assertThat(decision.isCacheAllowed()).isFalse();
        assertThat(metrics.count("cache.list.bypass_ahead")).isEqualTo(1);
        verify(cache, never()).publishMaxVersion(anyString(), anyLong());
    }

    @Test
    void mysqlFailureOrInvalidVersionFailsClosedInsteadOfUsingRedis() {
        when(authority.readListVersion()).thenThrow(new RuntimeException("mysql down"));
        assertThatThrownBy(gate::listVersion)
                .isInstanceOf(ProductCatalogSourceUnavailableException.class)
                .hasMessageContaining("catalog version");
        verifyNoInteractions(cache);

        reset(authority, cache);
        when(authority.readListVersion()).thenReturn(0L);
        assertThatThrownBy(gate::listVersion)
                .isInstanceOf(ProductCatalogSourceUnavailableException.class);
        verifyNoInteractions(cache);
    }
}
