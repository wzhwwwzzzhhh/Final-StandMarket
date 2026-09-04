package com.fashion.product;

import com.fashion.entity.ProductProjectionStatusSummary;
import com.fashion.entity.ProductProjectionTaskView;
import com.fashion.mapper.ProductCatalogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ProductProjectionOperationsService {
    private static final Set<String> STATUSES = new HashSet<>(Arrays.asList(
            "PENDING", "PROCESSING", "RETRY_WAIT", "SUCCEEDED", "SUPERSEDED", "FAILED_TERMINAL"));

    private final ProductCatalogMapper mapper;
    private final ProductProjectionMetrics metrics;

    public ProductProjectionOperationsService(ProductCatalogMapper mapper) {
        this(mapper, new ProductProjectionMetrics());
    }

    @Autowired
    public ProductProjectionOperationsService(ProductCatalogMapper mapper,
                                              ProductProjectionMetrics metrics) {
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public Map<String, Long> metrics() {
        return metrics.snapshot();
    }

    public List<ProductProjectionStatusSummary> status() {
        return mapper.summarizeProjectionTasks();
    }

    public List<ProductProjectionTaskView> tasks(String target, String status, int limit) {
        if (target != null && !("REDIS".equals(target) || "ES".equals(target))) {
            throw new IllegalArgumentException("invalid projection target");
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new IllegalArgumentException("invalid projection status");
        }
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be 1..200");
        return mapper.listProjectionTaskViews(target, status, limit);
    }

    @Transactional
    public void replay(long id) {
        if (id <= 0) throw new IllegalArgumentException("task id is required");
        if (mapper.replayTerminalProjectionTask(id) != 1) {
            throw new IllegalStateException("projection task is not replayable");
        }
    }
}
