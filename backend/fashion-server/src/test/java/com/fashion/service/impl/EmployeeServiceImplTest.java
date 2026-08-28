package com.fashion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.dto.AdminLoginDto;
import com.fashion.entity.Employee;
import com.fashion.entity.PageResult;
import com.fashion.mapper.EmployeeMapper;
import com.fashion.result.Result;
import com.github.pagehelper.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("B0 员工身份安全")
class EmployeeServiceImplTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("员工分页响应不序列化密码")
    void pageEmployeesReturnsSafeRecords() throws Exception {
        Page<Employee> employees = new Page<>();
        employees.add(employeeWithPassword());
        when(employeeMapper.list(null)).thenReturn(employees);

        PageResult<?> result = employeeService.pageEmployees(1, 10, null);
        JsonNode json = objectMapper.valueToTree(result.getRecords().get(0));

        assertFalse(json.has("password"));
        assertEquals("admin", json.get("username").asText());
    }

    @Test
    @DisplayName("员工详情响应不序列化密码")
    void getByIdReturnsSafeEmployee() throws Exception {
        when(employeeMapper.getById(6L)).thenReturn(employeeWithPassword());

        Object result = employeeService.getById(6L);
        JsonNode json = objectMapper.valueToTree(result);

        assertFalse(json.has("password"));
        assertEquals("管理员", json.get("name").asText());
    }

    @Test
    @DisplayName("管理员登录 Redis Hash 只保存最小身份字段")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void loginStoresOnlyMinimalIdentityInRedis() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Employee employee = employeeWithPassword();
        when(employeeMapper.getByUsername("admin")).thenReturn(employee);

        Result<?> result = employeeService.login(new AdminLoginDto("admin", "correct-password"));

        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(anyString(), mapCaptor.capture());
        assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("id", "name", "username", "phone")),
                mapCaptor.getValue().keySet()
        );
        assertEquals(1, result.getCode());
    }

    @Test
    @DisplayName("管理员明文存量密码不能继续登录")
    void loginRejectsLegacyPlaintextPassword() {
        Employee employee = employeeWithPassword();
        employee.setPassword("legacy-plaintext");
        when(employeeMapper.getByUsername("admin")).thenReturn(employee);

        Result<?> result = employeeService.login(new AdminLoginDto("admin", "legacy-plaintext"));

        assertEquals(0, result.getCode());
        verify(hashOperations, never()).putAll(anyString(), any());
    }

    @Test
    @DisplayName("员工资料更新不能修改密码")
    void updateIgnoresPassword() {
        Employee employee = employeeWithPassword();
        employee.setPassword("injected-password");
        when(employeeMapper.update(any(Employee.class))).thenReturn(1);

        employeeService.update(employee);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).update(captor.capture());
        assertEquals(null, captor.getValue().getPassword());
    }

    @Test
    @DisplayName("新增员工写入 BCrypt 并补齐数据库必需默认值")
    void saveHashesPasswordAndSetsRequiredDefaults() {
        Employee employee = new Employee();
        employee.setName("新员工");
        employee.setUsername("new-employee");
        employee.setPhone("13800138009");
        employee.setSex("男");
        employee.setPassword("raw-password");
        when(employeeMapper.save(any(Employee.class))).thenReturn(1);

        employeeService.save(employee);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).save(captor.capture());
        Employee saved = captor.getValue();
        assertEquals(1, saved.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getCreateTime());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getUpdateTime());
        org.junit.jupiter.api.Assertions.assertTrue(
                new BCryptPasswordEncoder().matches("raw-password", saved.getPassword())
        );
    }

    private Employee employeeWithPassword() {
        Employee employee = new Employee();
        employee.setId(6L);
        employee.setName("管理员");
        employee.setUsername("admin");
        employee.setPhone("13800138000");
        employee.setSex("男");
        employee.setIdNumber("110101199001011234");
        employee.setStatus(1);
        employee.setPassword(new BCryptPasswordEncoder().encode("correct-password"));
        return employee;
    }
}
