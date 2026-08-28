package com.fashion.security;

import com.fashion.entity.Employee;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B0 Mapper 密码写入边界")
class MapperSecurityContractTest {

    @Test
    @DisplayName("用户资料更新 SQL 不包含 password 且存在专用密码更新")
    void userMapperSeparatesProfileAndPasswordUpdates() throws Exception {
        String xml = readResource("/mapper/UserMapper.xml");
        String profileUpdate = between(xml, "<update id=\"update\"", "</update>");

        assertFalse(profileUpdate.contains("password"));
        assertTrue(xml.contains("<update id=\"updatePassword\""));
    }

    @Test
    @DisplayName("员工新增 SQL 写入 username 和 BCrypt password 字段")
    void employeeInsertPersistsUsernameAndPassword() throws Exception {
        String xml = readResource("/mapper/EmployeeMapper.xml");
        String insert = between(xml, "<insert id=\"save\"", "</insert>");

        assertTrue(insert.contains("username"));
        assertTrue(insert.contains("password"));
        assertTrue(insert.contains("#{password}"));
    }

    @Test
    @DisplayName("员工资料更新动态 SQL 只引用实体实际存在的白名单字段")
    void employeeUpdateUsesExistingWhitelistedProperties() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/mapper/EmployeeMapper.xml")) {
            assertTrue(input != null, "Missing resource: /mapper/EmployeeMapper.xml");
            new XMLMapperBuilder(input, configuration, "mapper/EmployeeMapper.xml",
                    configuration.getSqlFragments()).parse();
        }

        MappedStatement statement = configuration.getMappedStatement("com.fashion.mapper.EmployeeMapper.update");
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setName("安全更新");
        employee.setPhone("13800000000");
        employee.setSex("女");
        employee.setIdNumber("110101199001010000");
        employee.setStatus(1);
        employee.setUpdateTime(LocalDateTime.now());
        employee.setUpdateUser(2L);

        assertDoesNotThrow(() -> statement.getBoundSql(employee));
    }

    @Test
    @DisplayName("管理端用户分页查询密码哈希以正确派生 hasPassword")
    void userListSelectsPasswordForHasPasswordOnly() throws Exception {
        String xml = readResource("/mapper/UserMapper.xml");
        String list = between(xml, "<select id=\"list\"", "</select>");

        assertTrue(list.contains("password"));
    }

    private String readResource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing resource: " + path);
            }
            byte[] bytes = new byte[input.available()];
            int read = input.read(bytes);
            if (read != bytes.length) {
                throw new IOException("Incomplete resource read: " + path);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private String between(String value, String start, String end) {
        int startIndex = value.indexOf(start);
        assertTrue(startIndex >= 0, "missing start marker " + start);
        int endIndex = value.indexOf(end, startIndex);
        assertTrue(endIndex >= 0, "missing end marker " + end);
        return value.substring(startIndex, endIndex);
    }
}
