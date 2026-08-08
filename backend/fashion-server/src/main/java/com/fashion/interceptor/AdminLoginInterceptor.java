package com.fashion.interceptor;

import com.fashion.constant.RedisKey;
import com.fashion.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 管理端登录拦截器：校验 /admin/** 请求的管理端 token
 */
@Component
@Slf4j
public class AdminLoginInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            return unauthorized(response, "未登录");
        }

        // 上传接口同时服务管理端与用户端：任一有效 token 均放行
        String path = request.getRequestURI();
        if (path.startsWith("/upload/")) {
            Map<Object, Object> userMap = redisTemplate.opsForHash().entries(RedisKey.USER_LOGIN_KEY + token);
            if (!userMap.isEmpty()) {
                Object uid = userMap.get("id");
                if (uid != null) {
                    BaseContext.setUserId(Long.parseLong(uid.toString()));
                    return true;
                }
            }
        }

        Map<Object, Object> map = redisTemplate.opsForHash().entries(RedisKey.ADMIN_LOGIN_KEY + token);
        if (map.isEmpty()) {
            return unauthorized(response, "登录已过期，请重新登录");
        }

        Object employeeId = map.get("id");
        if (employeeId == null) {
            return unauthorized(response, "登录已过期，请重新登录");
        }
        BaseContext.setAdminId(Long.parseLong(employeeId.toString()));
        // 活跃即续期，与用户 token 行为保持一致
        redisTemplate.expire(RedisKey.ADMIN_LOGIN_KEY + token, 30 * 60, TimeUnit.SECONDS);
        return true;
    }

    /**
     * 请求结束后清理管理端上下文，防止 Tomcat 线程池复用导致身份串用
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeAdminId();
    }

    private boolean unauthorized(HttpServletResponse response, String msg) throws Exception {
        log.info("管理端鉴权失败：{}", msg);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\": 401, \"msg\": \"" + msg + "\", \"data\": null}");
        return false;
    }
}
