package com.campus.oj.service;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessClockTest {
    @Test
    void usesBeijingTimeZone() {
        assertEquals(ZoneId.of("Asia/Shanghai"), BusinessClock.ZONE);
        assertEquals(BusinessClock.ZONE, BusinessClock.now().atZone(BusinessClock.ZONE).getZone());
    }
}
