package com.fashion.seckill;

import io.lettuce.core.output.NestedMultiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("B6 Redis SCAN 单页内存门禁")
class B6RedisScanPageReaderTest {

    @Test
    @DisplayName("Lettuce SCAN 使用嵌套多值输出保留游标和成员")
    @SuppressWarnings("unchecked")
    void usesNestedMultiOutputForLettuceScan() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LettuceConnection connection = mock(LettuceConnection.class);
        when(redis.execute(any(RedisCallback.class), eq(true))).thenAnswer(invocation ->
                ((RedisCallback<Object>) invocation.getArgument(0)).doInRedis(connection));
        when(connection.execute(eq("SSCAN"), any(NestedMultiOutput.class),
                any(byte[].class), any(byte[].class), any(byte[].class), any(byte[].class)))
                .thenReturn(Arrays.asList(bytes("0"), Collections.singletonList(bytes("19"))));

        SeckillRedisScanPageReader.ScanPage<String> page =
                new SeckillRedisScanPageReader(redis).scanRegistry("0", 1);

        assertEquals(Collections.singletonList("19"), page.getEntries());
    }

    @Test
    @DisplayName("COUNT 是 hint，少量超额响应仍完整返回")
    void acceptsSmallPageLargerThanCountHint() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisCallback.class), eq(true))).thenReturn(Arrays.asList(bytes("0"),
                Arrays.asList(bytes("19"), bytes("20"))));

        SeckillRedisScanPageReader.ScanPage<String> page =
                new SeckillRedisScanPageReader(redis).scanRegistry("0", 1);

        assertEquals(Arrays.asList("19", "20"), page.getEntries());
    }

    @Test
    @DisplayName("超过硬上限的单次 Redis bucket 被拒绝而不物化业务对象")
    void rejectsRawPageLargerThanHardLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        List<byte[]> oversized = Collections.nCopies(5001, bytes("19"));
        when(redis.execute(any(RedisCallback.class), eq(true))).thenReturn(Arrays.asList(bytes("0"), oversized));

        assertThrows(IllegalStateException.class,
                () -> new SeckillRedisScanPageReader(redis).scanRegistry("0", 1));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
