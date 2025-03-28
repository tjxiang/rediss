package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheUtil {
    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    public CacheUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String prefix, ID id, Class<R> type, Function<ID, R> callback, Long time, TimeUnit unit) {
        String json = stringRedisTemplate.opsForValue().get(prefix + id);
        if (StrUtil.isNotBlank(json)) {
            JSONUtil.toBean(json, type);
        }

        if (json != null) {
            return null;
        }

        R r = callback.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue().set(prefix + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(prefix + id, r, time, unit);
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(String prefix, ID id, Class<R> type, Function<ID, R> callback, Long time, TimeUnit unit) {
        String json = stringRedisTemplate.opsForValue().get(prefix + id);
        if (StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        Boolean flag = tryLock(LOCK_SHOP_KEY + id);
        if (BooleanUtil.isTrue(flag)) {
            pool.submit(() -> {
                try {
                    callback.apply(id);
                    setWithLogicalExpire(prefix + id, r, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(LOCK_SHOP_KEY + id);
                }

            });
        }
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        return r;
    }

    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private Boolean unLock(String key) {
        Boolean flag = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(flag);
    }

}
