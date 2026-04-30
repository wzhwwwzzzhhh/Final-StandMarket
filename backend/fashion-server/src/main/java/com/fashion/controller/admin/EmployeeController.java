package com.fashion.controller.admin;

import com.fashion.entity.Employee;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    /**
     * 新增员工
     */
    @PostMapping
    public Result<String> save(@RequestBody Employee employee) {
        employeeService.save(employee);
        return Result.success();
    }

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public Result<PageResult<Employee>> page(int page, int pageSize, String name) {
        // 调用Service层的分页查询方法
        PageResult<Employee> pageResult = employeeService.pageEmployees(page, pageSize, name);
        return Result.success(pageResult);
    }

    /**
     * 删除员工
     */
    @DeleteMapping
    public Result<String> delete(@RequestParam Long id) {
        employeeService.removeById(id);
        return Result.success();
    }

    /**
     * 修改员工
     */
    @PutMapping
    public Result<String> update(@RequestBody Employee employee) {
        employeeService.update(employee);
        return Result.success();
    }

    /**
     * 根据id查询员工
     */
    @GetMapping("/getById")
    public Result<Employee> getById(@RequestParam Long id) {
        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }
}
