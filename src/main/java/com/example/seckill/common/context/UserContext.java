package com.example.seckill.common.context;

/**
 * 用户上下文：在请求线程内保存当前登录用户ID
 *
 * <p>为什么用 ThreadLocal：一次 HTTP 请求由单一线程处理，
 * JWT 拦截器把 userId 放入 ThreadLocal，Controller/Service 随时可取，
 * 请求结束（afterCompletion）必须 remove，防止线程池复用导致"数据串号"。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}