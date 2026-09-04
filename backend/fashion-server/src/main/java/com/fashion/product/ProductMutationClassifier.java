package com.fashion.product;

import com.fashion.entity.Product;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProductMutationClassifier {

    private ProductMutationClassifier() {
    }

    public static ProductMutationKind classify(Product existing, Product requested) {
        if (existing == null || requested == null) {
            throw new IllegalArgumentException("existing and requested products are required");
        }
        boolean stock = requested.getStock() != null
                && !Objects.equals(existing.getStock(), requested.getStock());
        boolean catalog = changed(existing.getName(), requested.getName())
                || changed(existing.getDescription(), requested.getDescription())
                || changedDecimal(existing.getPrice(), requested.getPrice())
                || changed(existing.getImage(), requested.getImage())
                || changed(existing.getCategoryId(), requested.getCategoryId())
                || changed(existing.getTag(), requested.getTag())
                || changed(existing.getStatus(), requested.getStatus())
                || changed(existing.getSales(), requested.getSales());
        if (stock && catalog) {
            return ProductMutationKind.MIXED;
        }
        if (stock) {
            return ProductMutationKind.STOCK_ONLY;
        }
        if (catalog) {
            return ProductMutationKind.CATALOG_ONLY;
        }
        return ProductMutationKind.NO_OP;
    }

    private static boolean changed(Object current, Object requested) {
        return requested != null && !Objects.equals(current, requested);
    }

    private static boolean changedDecimal(BigDecimal current, BigDecimal requested) {
        if (requested == null) {
            return false;
        }
        return current == null || current.compareTo(requested) != 0;
    }
}
