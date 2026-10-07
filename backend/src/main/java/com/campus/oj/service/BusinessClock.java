package com.campus.oj.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 项目业务时间统一使用北京时间，避免依赖服务器操作系统的默认时区。
 */
public final class BusinessClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BusinessClock() { }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
