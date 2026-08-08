package com.fashion.controller.admin;

import com.fashion.entity.OperationLog;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端操作日志（审计）查询
 */
@RestController
@RequestMapping("/admin/operationLog")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 分页查询操作日志
     */
    @GetMapping("/page")
    public Result<PageResult<OperationLog>> page(@RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "10") Integer size,
                                                  @RequestParam(required = false) String module,
                                                  @RequestParam(required = false) String keyword) {
        PageResult<OperationLog> result = operationLogService.page(page, size, module, keyword);
        return Result.success(result);
    }
}
