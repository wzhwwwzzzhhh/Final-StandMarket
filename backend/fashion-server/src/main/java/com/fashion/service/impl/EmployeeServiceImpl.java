package com.fashion.service.impl;

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
import com.fashion.vo.EmployeeSafeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
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
    public List<EmployeeSafeVO> list(String name) {
        List<EmployeeSafeVO> result = new java.util.ArrayList<>();
        for (Employee employee : employeeMapper.list(name)) {
            result.add(toSafeVO(employee));
        }
        return result;
    }
    
    @Override
    public PageResult<EmployeeSafeVO> pageEmployees(int page, int pageSize, String name) {
        // 开始分页
        PageHelper.startPage(page, pageSize);
        // 执行查询
        List<Employee> employees = employeeMapper.list(name);
        // 包装成PageInfo
        PageInfo<Employee> pageInfo = new PageInfo<>(employees);
        List<EmployeeSafeVO> safeEmployees = new java.util.ArrayList<>();
        for (Employee employee : pageInfo.getList()) {
            safeEmployees.add(toSafeVO(employee));
        }
        // 构造PageResult返回
        return new PageResult<>(pageInfo.getTotal(), safeEmployees);
    }
    
    @Override
    public EmployeeSafeVO getById(Long id) {
        return toSafeVO(employeeMapper.getById(id));
    }
    
    @Override
    public boolean save(Employee employee) {
        if (employee.getPassword() != null && !employee.getPassword().isEmpty()) {
            employee.setPassword(passwordEncoder.encode(employee.getPassword()));
        }
        LocalDateTime now = LocalDateTime.now();
        if (employee.getStatus() == null) {
            employee.setStatus(1);
        }
        if (employee.getCreateTime() == null) {
            employee.setCreateTime(now);
        }
        if (employee.getUpdateTime() == null) {
            employee.setUpdateTime(now);
        }
        return employeeMapper.save(employee) > 0;
    }
    
    @Override
    public boolean update(Employee employee) {
        employee.setPassword(null);
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
        boolean passwordOk = dbPassword != null
                && dbPassword.startsWith("$2")
                && passwordEncoder.matches(adminLoginDto.getPassword(), dbPassword);
        if (!passwordOk) {
            log.warn("管理端登录失败：用户名={}", adminLoginDto.getUsername());
            return Result.error("密码错误");
        }

        String token = UUID.randomUUID().toString();
        Map<String, Object> empMap = new HashMap<>();
        putIfNotNull(empMap, "id", employee.getId());
        putIfNotNull(empMap, "name", employee.getName());
        putIfNotNull(empMap, "username", employee.getUsername());
        putIfNotNull(empMap, "phone", employee.getPhone());
        redisTemplate.opsForHash().putAll(RedisKey.ADMIN_LOGIN_KEY + token, empMap);
        redisTemplate.expire(RedisKey.ADMIN_LOGIN_KEY + token, 30 * 60, TimeUnit.SECONDS);
        log.info("管理端登录成功：username={}", adminLoginDto.getUsername());

        AdminLoginVo vo = new AdminLoginVo();
        vo.setToken(token);
        vo.setEmployeeId(employee.getId());
        vo.setName(employee.getName());
        return Result.success(vo);
    }

    private EmployeeSafeVO toSafeVO(Employee employee) {
        if (employee == null) {
            return null;
        }
        EmployeeSafeVO vo = new EmployeeSafeVO();
        vo.setId(employee.getId());
        vo.setName(employee.getName());
        vo.setUsername(employee.getUsername());
        vo.setPhone(employee.getPhone());
        vo.setSex(employee.getSex());
        vo.setIdNumber(employee.getIdNumber());
        vo.setStatus(employee.getStatus());
        vo.setCreateTime(employee.getCreateTime());
        vo.setUpdateTime(employee.getUpdateTime());
        vo.setCreateUser(employee.getCreateUser());
        vo.setUpdateUser(employee.getUpdateUser());
        return vo;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value.toString());
        }
    }
}
