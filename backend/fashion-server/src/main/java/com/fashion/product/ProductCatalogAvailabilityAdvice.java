package com.fashion.product;

import com.fashion.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProductCatalogAvailabilityAdvice {

    @ExceptionHandler(ProductCatalogSourceUnavailableException.class)
    public ResponseEntity<Result<Void>> handle(ProductCatalogSourceUnavailableException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.error("PRODUCT_CATALOG_SOURCE_UNAVAILABLE"));
    }
}
