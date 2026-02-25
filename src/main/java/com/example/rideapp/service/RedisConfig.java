package com.example.rideapp.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {

    // 1. For general use (JSON objects, idempotency, etc.)
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());

        // Use the library-provided JSON serializer (non-deprecated factory method)
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    // 2. Dedicated for GEO + strings (driver locations, idempotency keys)
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    // Now inject the right one for geo
    @Bean
    public GeoOperations<String, String> geoOperations(StringRedisTemplate stringRedisTemplate) {
        return stringRedisTemplate.opsForGeo();
    }
}