package com.fashion.config;

import com.fashion.interceptor.AdminLoginInterceptor;
import com.fashion.interceptor.JwtUserInterceptor;
import com.fashion.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class Webconfig  implements WebMvcConfigurer {
    @Autowired
    private JwtUserInterceptor jwtUserInterceptor;
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private AdminLoginInterceptor adminLoginInterceptor;

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {

        // 管理端鉴权：拦截 /admin/**，放行登录接口；同时保护 OSS 上传（此前完全匿名可调）
        registry.addInterceptor(adminLoginInterceptor)
                .addPathPatterns("/admin/**", "/upload/**")
                .excludePathPatterns(
                        "/admin/employee/login"
                )
                .order(0);

        // 用户令牌拦截器：刷新用户 token 有效期并写入用户上下文，admin 路径交给管理端拦截器处理
        // 必须先于 loginInterceptor 执行，才能把 userId 写入上下文供其判断
        registry.addInterceptor(jwtUserInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/admin/**")
                .order(1);
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns(
                        // 登录相关
                        "/user/login",
                        "/user/register",
                        "/user/sms-code",
                        // 商品相关 - 不需要登录
                        "/user/product/**",
                        // 分类相关 - 不需要登录
                        "/user/category/**"
                )
                .order(2);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:3001", "http://localhost:3002", "http://localhost:3003")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*", "Access-Control-Allow-Origin", "Access-Control-Allow-Headers", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
