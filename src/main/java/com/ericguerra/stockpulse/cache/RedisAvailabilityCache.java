package com.ericguerra.stockpulse.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis hash per SKU: avail:{sku} -> {storeId: units}. A TTL bounds staleness if an update is ever
 * missed. Single-store updates use a Lua script so "update only if the hash exists" is atomic.
 */
@Component
@Profile("!test")
public class RedisAvailabilityCache implements AvailabilityCache {
    private static final DefaultRedisScript<Long> HSET_IF_EXISTS = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('HSET', KEYS[1], ARGV[1], ARGV[2]) return 1 end return 0", Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisAvailabilityCache(StringRedisTemplate redis, @Value("${stockpulse.cache-ttl:PT10M}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    private static String key(String sku) {
        return "avail:" + sku;
    }

    @Override
    public Optional<Map<String, Integer>> get(String sku) {
        Map<Object, Object> raw = redis.opsForHash().entries(key(sku));
        if (raw.isEmpty()) return Optional.empty();
        Map<String, Integer> out = new HashMap<>();
        raw.forEach((k, v) -> {
            if (!"_".equals(k)) out.put(k.toString(), Integer.parseInt(v.toString()));
        });
        return Optional.of(out);
    }

    @Override
    public void put(String sku, Map<String, Integer> byStore) {
        Map<String, String> values = new HashMap<>();
        byStore.forEach((k, v) -> values.put(k, Integer.toString(v)));
        values.put("_", "1"); // sentinel so "no stores carry this SKU" is cached too
        redis.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) conn -> {
            byte[] k = key(sku).getBytes();
            conn.keyCommands().del(k);
            Map<byte[], byte[]> bytes = new HashMap<>();
            values.forEach((field, value) -> bytes.put(field.getBytes(), value.getBytes()));
            conn.hashCommands().hMSet(k, bytes);
            conn.keyCommands().expire(k, ttl.toSeconds());
            return null;
        });
    }

    @Override
    public void updateIfPresent(String sku, String storeId, int available) {
        redis.execute(HSET_IF_EXISTS, List.of(key(sku)), storeId, Integer.toString(available));
    }

    @Override
    public void evict(String sku) {
        redis.delete(key(sku));
    }
}
