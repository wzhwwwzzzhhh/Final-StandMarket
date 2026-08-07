package com.fashion.constant;


public class RedisKey {

    public static final String USER_LOGIN_KEY = "user:login:"; // 用户登录key
    public static final String USER_LOGIN_CODE_KEY = "user:login:code:"; // 用户登录验证码key
    public static final String ADMIN_LOGIN_KEY = "admin:login:"; // 管理端登录key
    public static final String ORDER_NUMBER_SEQ_KEY = "order:number:seq"; // 订单号自增序列key（按日）
}
