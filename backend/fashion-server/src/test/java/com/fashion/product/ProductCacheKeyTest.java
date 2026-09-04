package com.fashion.product;

import com.fashion.dto.ProductQueryDTO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProductCacheKeyTest {

    @Test
    void normalizesUserQueryIntoStableLengthPrefixedUtf8Key() {
        ProductQueryDTO query = new ProductQueryDTO();
        query.setPage(2);
        query.setPageSize(20);
        query.setCategoryId(7L);
        query.setSortBy("price_asc");
        query.setKeyword("\tCoat \n");
        query.setTag("New");
        query.setIsSale(false);

        NormalizedProductQuery normalized = NormalizedProductQuery.forUser(query);

        assertThat(new String(normalized.canonicalBytes(), StandardCharsets.UTF_8))
                .isEqualTo("v1|2|20|7|price_asc|4:Coat|3:New|sale=1");
        assertThat(normalized.querySha256())
                .isEqualTo("a1149c924cb5b14a15dc0cc05db78aa3448f54214da9de6e9a625766ed35234f");
        assertThat(normalized.isSale()).isTrue();
        assertThat(ProductCacheKeys.list(42L, normalized))
                .isEqualTo("cache:product:list:v2:42:a1149c924cb5b14a15dc0cc05db78aa3448f54214da9de6e9a625766ed35234f");
    }

    @Test
    void usesUtf8ByteLengthWithoutChangingInternalWhitespaceOrCase() {
        ProductQueryDTO query = new ProductQueryDTO();
        query.setKeyword(" 衣  A ");
        query.setTag("夏");

        NormalizedProductQuery normalized = NormalizedProductQuery.forUser(query);

        assertThat(new String(normalized.canonicalBytes(), StandardCharsets.UTF_8))
                .contains("6:衣  A")
                .contains("3:夏")
                .contains("|sale=1");
    }

    @Test
    void rejectsValuesThatWouldCreateAmbiguousOrUnboundedQueries() {
        ProductQueryDTO query = new ProductQueryDTO();
        query.setPage(0);
        assertThatIllegalArgumentException().isThrownBy(() -> NormalizedProductQuery.forUser(query));

        query.setPage(1);
        query.setPageSize(101);
        assertThatIllegalArgumentException().isThrownBy(() -> NormalizedProductQuery.forUser(query));

        query.setPageSize(10);
        query.setCategoryId(0L);
        assertThatIllegalArgumentException().isThrownBy(() -> NormalizedProductQuery.forUser(query));

        query.setCategoryId(null);
        query.setSortBy("unknown");
        assertThatIllegalArgumentException().isThrownBy(() -> NormalizedProductQuery.forUser(query));
    }
}
