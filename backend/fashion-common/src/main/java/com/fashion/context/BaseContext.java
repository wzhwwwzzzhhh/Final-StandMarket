package com.fashion.context;

public class BaseContext {
    private static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
    private  static ThreadLocal<Long> userThreadLocal = new ThreadLocal<>();
    private static ThreadLocal<Long> adminThreadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }
    public static void setUserId(Long userId) {
        userThreadLocal.set(userId);
    }
    public static Long getUserId() {
        return userThreadLocal.get();
    }
    public static void removeUserId() {
        userThreadLocal.remove();
    }
    public static void setAdminId(Long adminId) {
        adminThreadLocal.set(adminId);
    }
    public static Long getAdminId() {
        return adminThreadLocal.get();
    }
    public static void removeAdminId() {
        adminThreadLocal.remove();
    }
}

