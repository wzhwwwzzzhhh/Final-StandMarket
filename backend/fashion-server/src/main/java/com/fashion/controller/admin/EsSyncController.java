package com.fashion.controller.admin;

import com.fashion.result.Result;
import com.fashion.service.ProductIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ES 同步管理
 */
@RestController
@RequestMapping("/admin/es")
public class EsSyncController {

    @Autowired
    private ProductIndexService productIndexService;

    /**
     * 全量重建索引（删除 + 创建 + 同步所有商品）
     */
    @PostMapping("/sync")
    public Result<String> syncAll() {
        try {
            productIndexService.rebuildIndex();
            return Result.success("全量同步完成");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查看 ES 索引状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = productIndexService.getIndexStatus();
        return Result.success(status);
    }
}
