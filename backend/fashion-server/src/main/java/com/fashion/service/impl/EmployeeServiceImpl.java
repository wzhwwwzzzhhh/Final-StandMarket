package com.fashion.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fashion.entity.Employee;
import com.fashion.entity.PageResult;
import com.fashion.mapper.EmployeeMapper;
import com.fashion.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {
    
    @Autowired
    private EmployeeMapper employeeMapper;
    
    @Override
    public List<Employee> list(String name) {
        return employeeMapper.list(name);
    }
    
    @Override
    public PageResult<Employee> pageEmployees(int page, int pageSize, String name) {
        // 开始分页
        PageHelper.startPage(page, pageSize);
        // 执行查询
        List<Employee> employees = employeeMapper.list(name);
        // 包装成PageInfo
        PageInfo<Employee> pageInfo = new PageInfo<>(employees);
        // 构造PageResult返回
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }
    
    @Override
    public Employee getById(Long id) {
        return employeeMapper.getById(id);
    }
    
    @Override
    public boolean save(Employee employee) {
        return employeeMapper.save(employee) > 0;
    }
    
    @Override
    public boolean update(Employee employee) {
        return employeeMapper.update(employee) > 0;
    }
    
    @Override
    public boolean removeById(Long id) {
        return employeeMapper.deleteById(id) > 0;
    }
}
