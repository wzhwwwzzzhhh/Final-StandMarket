package com.fashion.controller.admin;

import com.fashion.common.annotation.OperationLog;
import com.fashion.result.Result;
import com.fashion.entity.ProductReconciliationStatusView;
import com.fashion.product.ProductReconciliationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * ES 同步管理
 */
@RestController
@RequestMapping("/admin/es")
@Slf4j
public class EsSyncController {

    private final ProductReconciliationService reconciliationService;

    public EsSyncController(ProductReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /** 创建一个非破坏性的 MySQL/ES 对账任务。 */
    @PostMapping("/sync")
    @OperationLog(module = "ES同步", operation = "创建非破坏性对账")
    public Result<String> syncAll() {
        try {
            Long runId = reconciliationService.start("CUTOVER").getId();
            return Result.success("已创建非破坏性对账任务 runId=" + runId);
        } catch (RuntimeException e) {
            log.error("创建商品对账任务失败, errorType={}", e.getClass().getSimpleName());
            return Result.error("创建商品对账任务失败");
        }
    }

    /**
     * 查看 ES 索引状态
     */
    @GetMapping("/status")
    public Result<ProductReconciliationStatusView> status() {
        return Result.success(reconciliationService.status());
    }
}
