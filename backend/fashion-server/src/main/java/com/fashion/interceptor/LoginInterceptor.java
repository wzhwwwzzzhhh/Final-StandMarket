package com.fashion.interceptor;

import com.fashion.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 判断用户是否已经登录
     */

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(BaseContext.getUserId() == null){
            log.info("用户未登录");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\": 401, \"msg\": \"用户未登录\", \"data\": null}");
            return false;
        }
        return true;
    }
}
