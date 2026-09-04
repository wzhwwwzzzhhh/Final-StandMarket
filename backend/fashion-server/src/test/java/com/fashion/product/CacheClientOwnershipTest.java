package com.fashion.product;

import com.fashion.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CacheClientOwnershipTest {

    @Test
    void lockTokensAreUniqueAndOnlyOwnerTokenIsPassedToAtomicRelease() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("lock:product:1"), anyString(), eq(10_000L), eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenReturn(true, true);
        when(redis.execute(any(), anyList(), any())).thenReturn(1L);
        CacheClient cacheClient = new CacheClient(redis);

        String first = cacheClient.tryLockToken("lock:product:1", Duration.ofSeconds(10));
        String second = cacheClient.tryLockToken("lock:product:1", Duration.ofSeconds(10));
        Long released = cacheClient.releaseLock("lock:product:1", first);

        assertThat(first).isNotBlank().isNotEqualTo(second);
        assertThat(released).isEqualTo(1L);
        ArgumentCaptor<Object> token = ArgumentCaptor.forClass(Object.class);
        verify(redis).execute(any(), eq(java.util.Collections.singletonList("lock:product:1")), token.capture());
        assertThat(token.getValue()).isEqualTo(first);
        verify(redis, never()).delete("lock:product:1");
    }

    @Test
    void failedAcquisitionReturnsNullAndHasNoReleaseCapability() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(false);

        assertThat(new CacheClient(redis).tryLockToken("lock:product:1", Duration.ofSeconds(10))).isNull();
        verify(redis, never()).execute(any(), anyList(), any());
    }
}
