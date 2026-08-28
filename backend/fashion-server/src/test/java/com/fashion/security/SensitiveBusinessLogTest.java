package com.fashion.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fashion.dto.UserLoginDto;
import com.fashion.entity.User;
import com.fashion.mapper.UserMapper;
import com.fashion.result.Result;
import com.fashion.service.impl.UserServiceImpl;
import com.fashion.vo.UserLoginVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("B0 业务日志敏感信息保护")
class SensitiveBusinessLogTest {

    @Test
    @DisplayName("用户登录日志不包含 Token 或 BCrypt 哈希")
    void loginDoesNotLogTokenOrPasswordHash() {
        UserMapper userMapper = mock(UserMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        User user = new User();
        user.setId(7L);
        user.setName("测试用户");
        user.setPhone("13800000007");
        String passwordHash = new BCryptPasswordEncoder().encode("correct-password");
        user.setPassword(passwordHash);
        when(userMapper.selectByPhone(user.getPhone())).thenReturn(user);

        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);

        LogCapture capture = attach(UserServiceImpl.class);
        try {
            Result<UserLoginVo> result = service.login(
                    new UserLoginDto(user.getPhone(), "correct-password", null, "password"),
                    null
            );
            String logs = capture.messages();
            assertFalse(logs.contains(passwordHash));
            assertFalse(logs.contains(result.getData().getToken()));
        } finally {
            capture.detach();
        }
    }

    @Test
    @DisplayName("发送短信验证码时不记录验证码值")
    void smsCodeValueIsNotLogged() {
        UserMapper userMapper = mock(UserMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);

        LogCapture capture = attach(UserServiceImpl.class);
        try {
            service.sendSmsCode("13800000007");
            ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), codeCaptor.capture(), eq(120L), eq(TimeUnit.SECONDS));
            String logs = capture.messages();
            assertFalse(logs.contains("发送验证码："));
            assertFalse(logs.contains(codeCaptor.getValue()));
        } finally {
            capture.detach();
        }
    }

    private LogCapture attach(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        return new LogCapture(logger, appender);
    }

    private static class LogCapture {
        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender;

        private LogCapture(Logger logger, ListAppender<ILoggingEvent> appender) {
            this.logger = logger;
            this.appender = appender;
        }

        private String messages() {
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
        }

        private void detach() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
