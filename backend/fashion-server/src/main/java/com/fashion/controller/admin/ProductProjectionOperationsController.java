package com.fashion.controller.admin;

import com.fashion.common.annotation.OperationLog;
import com.fashion.entity.ProductProjectionStatusSummary;
import com.fashion.entity.ProductProjectionTaskView;
import com.fashion.product.ProductProjectionOperationsService;
import com.fashion.result.Result;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/product-projections")
public class ProductProjectionOperationsController {
    private final ProductProjectionOperationsService service;

    public ProductProjectionOperationsController(ProductProjectionOperationsService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public Result<List<ProductProjectionStatusSummary>> status() {
        return Result.success(service.status());
    }

    @GetMapping("/metrics")
    public Result<Map<String, Long>> metrics() {
        return Result.success(service.metrics());
    }

    @GetMapping("/tasks")
    public Result<List<ProductProjectionTaskView>> tasks(
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(service.tasks(target, status, limit));
    }

    @PostMapping("/tasks/{id}/replay")
    @OperationLog(module = "商品投影", operation = "人工重放终态任务")
    public Result<String> replay(@PathVariable long id) {
        service.replay(id);
        return Result.success("已安排人工重放");
    }
}
