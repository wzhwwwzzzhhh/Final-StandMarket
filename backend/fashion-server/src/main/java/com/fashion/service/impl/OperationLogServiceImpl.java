package com.fashion.service.impl;

import com.fashion.entity.OperationLog;
import com.fashion.entity.PageResult;
import com.fashion.mapper.OperationLogMapper;
import com.fashion.service.OperationLogService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Override
    public void save(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }

    @Override
    public PageResult<OperationLog> page(Integer page, Integer size, String module, String keyword) {
        PageHelper.startPage(page, size);
        List<OperationLog> list = operationLogMapper.selectPage(module, keyword);
        PageInfo<OperationLog> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }
}
