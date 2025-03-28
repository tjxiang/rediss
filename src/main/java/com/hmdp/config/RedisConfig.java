package com.hmdp.config;

import jdk.nashorn.internal.runtime.regexp.joni.Config;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient(){
        return null;
    }
}
////////////////////////////////////////

// hot fix uodate
//master
