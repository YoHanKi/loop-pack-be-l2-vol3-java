package com.loopers.application.product;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class ProductCacheStore {

    private static final String KEY_PREFIX = "product::";
    private static final long TTL_SECONDS = 60;

    private final RedisTemplate<String, byte[]> redisTemplate;
    private final GenericJackson2JsonRedisSerializer serializer;

    public ProductCacheStore(RedisConnectionFactory redisConnectionFactory) {
        this.serializer = new GenericJackson2JsonRedisSerializer();
        this.redisTemplate = buildRedisTemplate(redisConnectionFactory);
    }

    public Optional<ProductInfo> get(String productId) {
        byte[] bytes = redisTemplate.opsForValue().get(KEY_PREFIX + productId);
        if (bytes == null) {
            return Optional.empty();
        }
        return Optional.of((ProductInfo) serializer.deserialize(bytes));
    }

    public void put(String productId, ProductInfo productInfo) {
        byte[] bytes = serializer.serialize(productInfo);
        redisTemplate.opsForValue().set(KEY_PREFIX + productId, bytes, TTL_SECONDS, TimeUnit.SECONDS);
    }

    private RedisTemplate<String, byte[]> buildRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }
}
