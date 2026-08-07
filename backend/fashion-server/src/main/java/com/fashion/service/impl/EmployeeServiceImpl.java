package com.fashion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fashion.constant.RedisKey;
import com.fashion.dto.AdminLoginDto;
import com.fashion.entity.Employee;
import com.fashion.entity.PageResult;
import com.fashion.mapper.EmployeeMapper;
import com.fashion.result.Result;
import com.fashion.service.EmployeeService;
import com.fashion.vo.AdminLoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
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
        if (employee.getPassword() != null && !employee.getPassword().isEmpty()
                && !employee.getPassword().startsWith("$2")) {
            employee.setPassword(passwordEncoder.encode(employee.getPassword()));
        }
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

    @Override
    public Result<AdminLoginVo> login(AdminLoginDto adminLoginDto) {
        if (adminLoginDto == null || adminLoginDto.getUsername() == null || adminLoginDto.getPassword() == null) {
            return Result.error("用户名或密码不能为空");
        }
        Employee employee = employeeMapper.getByUsername(adminLoginDto.getUsername());
        if (employee == null) {
            return Result.error("账号不存在");
        }
        if (employee.getStatus() == null || employee.getStatus() != 1) {
            return Result.error("账号已被禁用");
        }
        String dbPassword = employee.getPassword();
        boolean passwordOk;
        if (dbPassword != null && dbPassword.startsWith("$2")) {
            // BCrypt 哈希比对
            passwordOk = passwordEncoder.matches(adminLoginDto.getPassword(), dbPassword);
        } else {
            // 兼容存量明文密码（迁移脚本执行前）
            passwordOk = adminLoginDto.getPassword().equals(dbPassword);
        }
        if (!passwordOk) {
            log.warn("管理端登录失败：用户名={}", adminLoginDto.getUsername());
            return Result.error("密码错误");
        }

        String token = UUID.randomUUID().toString();
        Map<String, Object> empMap = BeanUtil.beanToMap(employee, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                                fieldValue != null ? fieldValue.toString() : null));
        redisTemplate.opsForHash().putAll(RedisKey.ADMIN_LOGIN_KEY + token, empMap);
        redisTemplate.expire(RedisKey.ADMIN_LOGIN_KEY + token, 30 * 60, TimeUnit.SECONDS);
        log.info("管理端登录成功：username={}", adminLoginDto.getUsername());

        AdminLoginVo vo = new AdminLoginVo();
        vo.setToken(token);
        vo.setEmployeeId(employee.getId());
        vo.setName(employee.getName());
        return Result.success(vo);
    }
}
