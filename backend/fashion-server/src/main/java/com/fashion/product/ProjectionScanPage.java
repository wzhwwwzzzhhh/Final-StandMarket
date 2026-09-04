package com.fashion.product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProjectionScanPage {
    private final List<IndexedProductProjection> items;
    private final String nextCursor;

    private ProjectionScanPage(List<IndexedProductProjection> items, String nextCursor) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.nextCursor = nextCursor;
    }

    public static ProjectionScanPage of(List<IndexedProductProjection> items, String nextCursor) {
        return new ProjectionScanPage(items == null ? Collections.emptyList() : items, nextCursor);
    }

    public static ProjectionScanPage end() {
        return of(Collections.emptyList(), null);
    }

    public List<IndexedProductProjection> getItems() { return items; }
    public String getNextCursor() { return nextCursor; }
}
