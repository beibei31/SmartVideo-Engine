package com.example.server.context;

public final class TokenUsageContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private TokenUsageContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
