package com.fashion.product;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductProjectionPropertiesTest {

    @Test
    void defaultsBoundEveryEsCallAndReconciliationBatchInsideLease() {
        ProductProjectionProperties properties = new ProductProjectionProperties();

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThatCode(() -> properties.requireDeliveryWindow(Duration.ofSeconds(5)))
                .doesNotThrowAnyException();
    }

    @Test
    void startupRejectsTimeoutOrReconciliationBatchThatCanOutliveLease() {
        ProductProjectionProperties properties = new ProductProjectionProperties();
        properties.setSocketTimeout(Duration.ofSeconds(25));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lease");

        ProductProjectionProperties oversizedBatch = new ProductProjectionProperties();
        oversizedBatch.setReconcileBatchSize(20);
        assertThatThrownBy(oversizedBatch::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reconciliation batch");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationYamlKeepsAllProjectionSettingsAtTheBindingPrefix() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/application.yml")) {
            assertThat(input).isNotNull();
            Map<String, Object> root = new Yaml().load(input);
            Map<String, Object> fashion = (Map<String, Object>) root.get("fashion");
            Map<String, Object> projection = (Map<String, Object>) fashion.get("product-projection");

            assertThat(projection).containsKeys(
                    "max-attempts", "batch-size", "reconcile-batch-size", "lease",
                    "connect-timeout", "socket-timeout", "connection-request-timeout",
                    "lease-margin", "retry-base", "retry-max", "retry-jitter", "index-name");
        }
    }
}
