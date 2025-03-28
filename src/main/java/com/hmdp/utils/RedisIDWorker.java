package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIME = 1640995200L;
    private static final int COUNT_BITS = 32;

    public long nextId(String keyPrefix) {
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - BEGIN_TIME;
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd")));
        return timestamp << COUNT_BITS | count;
    }
}
