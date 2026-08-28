package com.fashion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.dto.UserLoginDto;
import com.fashion.entity.PageResult;
import com.fashion.entity.User;
import com.fashion.mapper.UserMapper;
import com.fashion.result.Result;
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

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("B0 用户身份安全")
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private HttpSession httpSession;

    @InjectMocks
    private UserServiceImpl userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("用户资料响应不序列化密码并返回 hasPassword")
    void getUserInfoReturnsSafeProfile() throws Exception {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "7");
        when(hashOperations.entries(anyString())).thenReturn(session);

        User user = userWithPassword();
        when(userMapper.selectById(7L)).thenReturn(user);

        Result<?> result = userService.getUserInfo("Bearer token-value");
        JsonNode json = objectMapper.valueToTree(result.getData());

        assertFalse(json.has("password"));
        assertTrue(json.get("hasPassword").asBoolean());
        assertEquals("测试用户", json.get("name").asText());
    }

    @Test
    @DisplayName("管理端用户分页记录不序列化密码")
    void pageUsersReturnsSafeRecords() throws Exception {
        when(userMapper.list(0, 10, null, null)).thenReturn(Collections.singletonList(userWithPassword()));
        when(userMapper.count(null, null)).thenReturn(1);

        PageResult<?> result = userService.pageUsers(1, 10, null, null);
        JsonNode json = objectMapper.valueToTree(result.getRecords().get(0));

        assertFalse(json.has("password"));
        assertTrue(json.get("hasPassword").asBoolean());
        assertEquals("测试用户", json.get("name").asText());
    }

    @Test
    @DisplayName("管理端新增用户写入 BCrypt")
    void adminSaveHashesPassword() {
        User user = userWithPassword();
        user.setPassword("raw-password");

        userService.save(user);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertTrue(new BCryptPasswordEncoder().matches("raw-password", captor.getValue().getPassword()));
    }

    @Test
    @DisplayName("用户注册写入 BCrypt")
    void registrationHashesPassword() {
        User user = userWithPassword();
        user.setPassword("raw-password");
        when(userMapper.selectByPhone(user.getPhone())).thenReturn(null);

        Result<?> result = userService.register(user);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertTrue(new BCryptPasswordEncoder().matches("raw-password", captor.getValue().getPassword()));
        assertEquals(1, result.getCode());
    }

    @Test
    @DisplayName("用户登录 Redis Hash 只保存最小身份字段")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void loginStoresOnlyMinimalIdentityInRedis() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        User user = userWithPassword();
        when(userMapper.selectByPhone(user.getPhone())).thenReturn(user);

        UserLoginDto request = new UserLoginDto(user.getPhone(), "correct-password", null, "password");
        Result<?> result = userService.login(request, httpSession);

        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(anyString(), mapCaptor.capture());
        assertEquals(
                new java.util.HashSet<>(java.util.Arrays.asList("id", "name", "phone", "avatar")),
                mapCaptor.getValue().keySet()
        );
        assertEquals(1, result.getCode());
    }

    @Test
    @DisplayName("明文存量密码不能继续登录")
    void loginRejectsLegacyPlaintextPassword() {
        User user = userWithPassword();
        user.setPassword("legacy-plaintext");
        when(userMapper.selectByPhone(user.getPhone())).thenReturn(user);

        Result<?> result = userService.login(
                new UserLoginDto(user.getPhone(), "legacy-plaintext", null, "password"),
                httpSession
        );

        assertEquals(0, result.getCode());
        verify(hashOperations, never()).putAll(anyString(), any());
    }

    @Test
    @DisplayName("资料更新即使夹带密码也不会写入密码")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void profileUpdateIgnoresPassword() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "7");
        when(hashOperations.entries(anyString())).thenReturn(session);

        User update = new User();
        update.setName("新名字");
        update.setPassword("injected-password");

        Result<?> result = userService.updateUserInfo("Bearer token-value", update);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(userCaptor.capture());
        assertEquals(null, userCaptor.getValue().getPassword());
        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(anyString(), mapCaptor.capture());
        assertFalse(mapCaptor.getValue().containsKey("password"));
        assertEquals(1, result.getCode());
    }

    @Test
    @DisplayName("管理端资料更新不能修改用户密码")
    void adminUpdateIgnoresPassword() {
        User update = new User();
        update.setId(7L);
        update.setName("新名字");
        update.setPassword("injected-password");

        userService.update(update);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(userCaptor.capture());
        assertEquals(null, userCaptor.getValue().getPassword());
    }

    @Test
    @DisplayName("首次设置密码写入 BCrypt")
    void firstPasswordSetUsesBcrypt() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "7");
        when(hashOperations.entries(anyString())).thenReturn(session);
        User user = userWithPassword();
        user.setPassword(null);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.updatePassword(org.mockito.ArgumentMatchers.eq(7L), anyString())).thenReturn(1);

        Result<?> result = userService.changePassword("Bearer token-value", null, "new-password");

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePassword(org.mockito.ArgumentMatchers.eq(7L), passwordCaptor.capture());
        assertTrue(new BCryptPasswordEncoder().matches("new-password", passwordCaptor.getValue()));
        assertEquals(1, result.getCode());
    }

    @Test
    @DisplayName("已设置密码时校验旧密码并写入新的 BCrypt")
    void existingPasswordChangeUsesBcrypt() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "7");
        when(hashOperations.entries(anyString())).thenReturn(session);
        when(userMapper.selectById(7L)).thenReturn(userWithPassword());
        when(userMapper.updatePassword(org.mockito.ArgumentMatchers.eq(7L), anyString())).thenReturn(1);

        Result<?> result = userService.changePassword(
                "Bearer token-value",
                "correct-password",
                "new-password"
        );

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePassword(org.mockito.ArgumentMatchers.eq(7L), passwordCaptor.capture());
        assertTrue(new BCryptPasswordEncoder().matches("new-password", passwordCaptor.getValue()));
        assertEquals(1, result.getCode());
    }

    @Test
    @DisplayName("旧密码错误时拒绝修改")
    void wrongOldPasswordIsRejected() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "7");
        when(hashOperations.entries(anyString())).thenReturn(session);
        when(userMapper.selectById(7L)).thenReturn(userWithPassword());

        Result<?> result = userService.changePassword(
                "Bearer token-value",
                "wrong-password",
                "new-password"
        );

        assertEquals(0, result.getCode());
        verify(userMapper, never()).updatePassword(any(Long.class), anyString());
    }

    @Test
    @DisplayName("密码 UPDATE 未命中用户时不能误报成功")
    void passwordUpdateWithoutAffectedRowReturnsError() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "7");
        when(hashOperations.entries(anyString())).thenReturn(session);
        User user = userWithPassword();
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.updatePassword(org.mockito.ArgumentMatchers.eq(7L), anyString())).thenReturn(0);

        Result<?> result = userService.changePassword(
                "Bearer token-value",
                "correct-password",
                "new-password"
        );

        assertEquals(0, result.getCode());
    }

    @Test
    @DisplayName("新密码少于六位时拒绝写入")
    void shortPasswordIsRejected() {
        Result<?> result = userService.changePassword("Bearer token-value", "correct-password", "12345");

        assertEquals(0, result.getCode());
        verify(userMapper, never()).updatePassword(any(Long.class), anyString());
    }

    private User userWithPassword() {
        User user = new User();
        user.setId(7L);
        user.setName("测试用户");
        user.setPhone("13800000007");
        user.setAvatar("avatar.png");
        user.setSex("男");
        user.setIdNumber("110101199001010011");
        user.setPassword(new BCryptPasswordEncoder().encode("correct-password"));
        return user;
    }
}
