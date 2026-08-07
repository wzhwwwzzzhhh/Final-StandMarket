package com.fashion.service;

import com.fashion.entity.OperationLog;
import com.fashion.entity.PageResult;

public interface OperationLogService {

    void save(OperationLog operationLog);

    PageResult<OperationLog> page(Integer page, Integer size, String module, String keyword);
}
