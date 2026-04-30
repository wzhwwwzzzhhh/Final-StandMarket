package com.fashion.mapper;

import com.fashion.entity.Employee;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface EmployeeMapper {
    /**
     * 根据名称查询员工列表
     * @param name 员工名称
     * @return 员工列表
     */
    List<Employee> list(String name);
    
    /**
     * 根据ID查询员工
     * @param id 员工ID
     * @return 员工
     */
    Employee getById(Long id);
    
    /**
     * 新增员工
     * @param employee 员工信息
     * @return 影响行数
     */
    int save(Employee employee);
    
    /**
     * 更新员工
     * @param employee 员工信息
     * @return 影响行数
     */
    int update(Employee employee);
    
    /**
     * 删除员工
     * @param id 员工ID
     * @return 影响行数
     */
    int deleteById(Long id);
}
