package com.fashion.product;

import com.fashion.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalProductProjectionCodecTest {

    @Test
    void exactUtf8PayloadAndHashExcludeStockAndNormalizeLegacySales() throws Exception {
        Product product = new Product();
        product.setId(7L);
        product.setName("衣\"\\");
        product.setDescription(null);
        product.setCategoryId(3L);
        product.setPrice(new BigDecimal("1"));
        product.setImage("图😀");
        product.setTag("夏");
        product.setStatus(1);
        product.setStock(999);
        product.setSales(null);

        CanonicalProductProjection projection = new CanonicalProductProjectionCodec().encode(product, 42L);
        String expected = "{\"id\":7,\"name\":\"衣\\\"\\\\\",\"description\":null,\"categoryId\":3,"
                + "\"price\":1.00,\"image\":\"图\\uD83D\\uDE00\",\"tag\":\"夏\",\"status\":1,\"sales\":0,"
                + "\"catalogVersion\":42}";

        assertThat(new String(projection.getPayload(), StandardCharsets.UTF_8)).isEqualTo(expected);
        assertThat(projection.getSha256()).isEqualTo(hex(MessageDigest.getInstance("SHA-256")
                .digest(expected.getBytes(StandardCharsets.UTF_8))));
        assertThat(expected).doesNotContain("stock").doesNotContain("projectionHash");
    }

    private String hex(byte[] digest) {
        StringBuilder result = new StringBuilder();
        for (byte value : digest) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
