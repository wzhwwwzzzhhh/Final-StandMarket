package com.fashion.service;

import com.fashion.dto.AdminLoginDto;
import com.fashion.entity.Employee;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.vo.AdminLoginVo;
import com.fashion.vo.EmployeeSafeVO;
import java.util.List;

public interface EmployeeService {
    /**
     * 根据名称查询员工列表
     * @param name 员工名称
     * @return 员工列表
     */
    List<EmployeeSafeVO> list(String name);
    
    /**
     * 分页查询员工
     * @param page 页码
     * @param pageSize 每页大小
     * @param name 员工名称
     * @return 分页后的员工列表
     */
    PageResult<EmployeeSafeVO> pageEmployees(int page, int pageSize, String name);
    
    /**
     * 根据ID查询员工
     * @param id 员工ID
     * @return 员工
     */
    EmployeeSafeVO getById(Long id);
    
    /**
     * 新增员工
     * @param employee 员工信息
     * @return 是否成功
     */
    boolean save(Employee employee);
    
    /**
     * 更新员工
     * @param employee 员工信息
     * @return 是否成功
     */
    boolean update(Employee employee);
    
    /**
     * 删除员工
     * @param id 员工ID
     * @return 是否成功
     */
    boolean removeById(Long id);

    /**
     * 管理端登录
     * @param adminLoginDto 登录参数
     * @return 登录结果（token）
     */
    Result<AdminLoginVo> login(AdminLoginDto adminLoginDto);
}
