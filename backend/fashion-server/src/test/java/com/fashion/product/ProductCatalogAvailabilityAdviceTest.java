package com.fashion.product;

import com.fashion.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCatalogAvailabilityAdviceTest {

    @Test
    void mysqlAuthorityFailureHasStable503Contract() {
        ProductCatalogAvailabilityAdvice advice = new ProductCatalogAvailabilityAdvice();

        ResponseEntity<Result<Void>> response = advice.handle(
                new ProductCatalogSourceUnavailableException("internal details must not leak"));

        assertThat(response.getStatusCodeValue()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isZero();
        assertThat(response.getBody().getMsg()).isEqualTo("PRODUCT_CATALOG_SOURCE_UNAVAILABLE");
    }
}
