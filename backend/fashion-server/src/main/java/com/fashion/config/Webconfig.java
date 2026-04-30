package com.fashion.config;

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

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {

        registry.addInterceptor(jwtUserInterceptor)
                .addPathPatterns("/**");
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns(
                        // 登录相关
                        "/user/login",
                        "/user/send-sms-code",
                        // 商品相关 - 不需要登录
                        "/user/product/**",
                        // 分类相关 - 不需要登录
                        "/user/category/**",
                        "/upload/**"
                )
                .order(1);
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
