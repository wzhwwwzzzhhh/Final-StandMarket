package com.fashion.interceptor;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.BeanToBeanCopier;
import com.fashion.constant.MessageConstant;
import com.fashion.constant.RedisKey;
import com.fashion.context.BaseContext;
import com.fashion.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户令牌拦截器
 * 用于刷新
 */
@Component
@Slf4j
public class JwtUserInterceptor extends HandlerInterceptorAdapter {
    @Autowired
    private StringRedisTemplate redisTemplate;
    /**
     * 拦截器
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @return 是否继续处理
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 从请求头中获取令牌
        String token = request.getHeader("Authorization");
        if (token == null) {
            log.info("用户令牌为空");
            return true;
        }
        
        // 处理Bearer前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        //验证令牌
        String tokenkey = RedisKey.USER_LOGIN_KEY + token;
        Map<Object, Object> map = redisTemplate.opsForHash().entries(tokenkey);
        if (map.isEmpty()) {
            log.info("用户令牌不存在");
            return true;
        }

        User user = BeanUtil.fillBeanWithMap(map, new User(), false);
        BaseContext.setUserId(user.getId());

        //用户登录成功，将用户ID设置到上下文
        log.info("用户登录成功，用户ID：{}", user.getId());
        //刷新token有效期
        redisTemplate.expire(tokenkey, MessageConstant.TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);

        return true;
    }
}
