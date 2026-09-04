package com.fashion.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchProductProjectionInventoryTest {

    @Test
    void parsesOnlyVersionAndHashMetadata() throws Exception {
        String body = "{\"_id\":\"7\",\"_source\":{" +
                "\"catalogVersion\":42,\"projectionHash\":\"abc\",\"description\":\"secret\"}}";

        IndexedProductProjection result = ElasticsearchProductProjectionInventory
                .parseDocument(new ObjectMapper(), body);

        assertThat(result.getProductId()).isEqualTo(7L);
        assertThat(result.getCatalogVersion()).isEqualTo(42L);
        assertThat(result.getProjectionHash()).isEqualTo("abc");
    }

    @Test
    void searchCursorIsOpaqueAndCarriesPitAndStableNumericSort() throws Exception {
        String body = "{\"pit_id\":\"new-pit\",\"hits\":{\"hits\":[{" +
                "\"_id\":\"7\",\"_source\":{\"catalogVersion\":42,\"projectionHash\":\"abc\"}," +
                "\"sort\":[7]}]}}";

        ProjectionScanPage page = ElasticsearchProductProjectionInventory
                .parseSearch(new ObjectMapper(), body);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getNextCursor()).isNotBlank().doesNotContain("new-pit");
        ElasticsearchProductProjectionInventory.ScanCursor cursor =
                ElasticsearchProductProjectionInventory.decodeCursor(
                        new ObjectMapper(), page.getNextCursor());
        assertThat(cursor.getPitId()).isEqualTo("new-pit");
        assertThat(cursor.getLastSort()).isEqualTo(7L);
    }
}
