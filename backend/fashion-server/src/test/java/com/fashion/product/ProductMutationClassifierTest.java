package com.fashion.product;

import com.fashion.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMutationClassifierTest {

    @Test
    void distinguishesNoopStockCatalogAndMixedChanges() {
        Product existing = product();

        Product noOp = new Product();
        noOp.setId(7L);
        noOp.setName("coat");
        assertThat(ProductMutationClassifier.classify(existing, noOp)).isEqualTo(ProductMutationKind.NO_OP);

        Product stock = new Product();
        stock.setId(7L);
        stock.setStock(9);
        assertThat(ProductMutationClassifier.classify(existing, stock)).isEqualTo(ProductMutationKind.STOCK_ONLY);

        Product catalog = new Product();
        catalog.setId(7L);
        catalog.setImage("new.jpg");
        assertThat(ProductMutationClassifier.classify(existing, catalog)).isEqualTo(ProductMutationKind.CATALOG_ONLY);

        Product mixed = new Product();
        mixed.setId(7L);
        mixed.setStock(9);
        mixed.setPrice(new BigDecimal("12.00"));
        assertThat(ProductMutationClassifier.classify(existing, mixed)).isEqualTo(ProductMutationKind.MIXED);
    }

    private Product product() {
        Product product = new Product();
        product.setId(7L);
        product.setName("coat");
        product.setDescription("warm");
        product.setCategoryId(3L);
        product.setPrice(new BigDecimal("10.00"));
        product.setImage("old.jpg");
        product.setTag("new");
        product.setStatus(1);
        product.setStock(10);
        product.setSales(0);
        return product;
    }
}
