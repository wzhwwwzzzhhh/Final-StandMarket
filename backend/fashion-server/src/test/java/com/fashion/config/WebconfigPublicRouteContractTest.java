package com.fashion.config;

import com.fashion.controller.user.UserCategoryController;
import com.fashion.controller.user.UserController;
import com.fashion.controller.user.UserProductController;
import com.fashion.interceptor.AdminLoginInterceptor;
import com.fashion.interceptor.JwtUserInterceptor;
import com.fashion.interceptor.LoginInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("B4 用户公开路由合约")
class WebconfigPublicRouteContractTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("匿名可访问真实注册验证码登录商品分类接口")
    void actualPublicRoutesAreAnonymous() throws Exception {
        mockMvc.perform(post("/user/register")).andExpect(status().isOk());
        mockMvc.perform(post("/user/sms-code")).andExpect(status().isOk());
        mockMvc.perform(post("/user/login")).andExpect(status().isOk());
        mockMvc.perform(get("/user/product/1")).andExpect(status().isOk());
        mockMvc.perform(get("/user/category/list")).andExpect(status().isOk());
        mockMvc.perform(get("/user/review/list/1")).andExpect(status().isOk());
        mockMvc.perform(get("/user/review/stats/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("错误旧验证码路径和其他用户接口仍需登录")
    void staleAndPrivateRoutesAreProtected() throws Exception {
        mockMvc.perform(post("/user/send-sms-code")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/private-resource")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/review/add")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/review/my")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/review/check/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/review/list/1/export")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("上传接口继续拒绝匿名请求")
    void uploadRemainsProtected() throws Exception {
        mockMvc.perform(get("/upload/file")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("公开白名单与真实用户 Controller 映射保持一致")
    void allowlistMatchesRealControllerMappings() {
        assertTrue(hasClassMapping(UserController.class, "/user"));
        assertTrue(hasPostMapping(UserController.class, "register", "/register"));
        assertTrue(hasPostMapping(UserController.class, "sendSmsCode", "/sms-code"));
        assertTrue(hasPostMapping(UserController.class, "login", "/login"));
        assertTrue(hasClassMapping(UserProductController.class, "/user/product"));
        assertTrue(hasClassMapping(UserCategoryController.class, "/user/category"));
    }

    private static boolean hasClassMapping(Class<?> controller, String path) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        return mapping != null && Arrays.asList(mapping.value()).contains(path);
    }

    private static boolean hasPostMapping(Class<?> controller, String methodName, String path) {
        for (Method method : controller.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                PostMapping mapping = method.getAnnotation(PostMapping.class);
                return mapping != null && Arrays.asList(mapping.value()).contains(path);
            }
        }
        return false;
    }

    @Configuration
    @EnableWebMvc
    @Import(Webconfig.class)
    static class TestWebConfig {

        @Bean
        JwtUserInterceptor jwtUserInterceptor() throws Exception {
            JwtUserInterceptor interceptor = mock(JwtUserInterceptor.class);
            when(interceptor.preHandle(any(), any(), any())).thenReturn(true);
            return interceptor;
        }

        @Bean
        LoginInterceptor loginInterceptor() {
            return new LoginInterceptor();
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        AdminLoginInterceptor adminLoginInterceptor() throws Exception {
            AdminLoginInterceptor interceptor = mock(AdminLoginInterceptor.class);
            doAnswer(invocation -> {
                HttpServletResponse response = invocation.getArgument(1);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }).when(interceptor).preHandle(any(), any(), any());
            return interceptor;
        }

        @Bean
        RouteProbeController routeProbeController() {
            return new RouteProbeController();
        }
    }

    @RestController
    static class RouteProbeController {

        @PostMapping({"/user/register", "/user/sms-code", "/user/login", "/user/send-sms-code",
                "/user/review/add"})
        String postRoute() {
            return "ok";
        }

        @GetMapping({"/user/product/1", "/user/category/list", "/user/private-resource", "/upload/file",
                "/user/review/list/1", "/user/review/stats/1", "/user/review/my", "/user/review/check/1",
                "/user/review/list/1/export"})
        String getRoute() {
            return "ok";
        }
    }
}
