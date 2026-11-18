package org.apache.shiro.spring.boot.cache;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.github.streamone.shiro.cache.RedissonShiroCache;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.spring.cache.CacheConfig;

/** 使用 Redisson 的原生 Map 和 MapCache 管理 Shiro 缓存区域。 */
final class ShiroRedissonCacheManager implements CacheManager {

    private final RedissonClient redisson;
    private final Codec codec;
    private final Map<String, ? extends CacheConfig> configs;
    private final boolean allowNullValues;
    private final Map<String, Cache<?, ?>> regions = new ConcurrentHashMap<>();

    ShiroRedissonCacheManager(RedissonClient redisson, Codec codec, Map<String, ? extends CacheConfig> configs,
                             boolean allowNullValues) {
        this.redisson = redisson;
        this.codec = codec;
        this.configs = configs;
        this.allowNullValues = allowNullValues;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name) {
        return (Cache<K, V>) regions.computeIfAbsent(name, this::createCache);
    }

    private Cache<?, ?> createCache(String name) {
        CacheConfig config = configs.get(name);
        if (Objects.isNull(config)
                || (config.getTTL() == 0 && config.getMaxIdleTime() == 0 && config.getMaxSize() == 0)) {
            return new RedissonShiroCache<>(Objects.isNull(codec) ? redisson.getMap(name) : redisson.getMap(name, codec),
                    allowNullValues);
        }
        RMapCache<Object, Object> map = Objects.isNull(codec)
                ? redisson.getMapCache(name) : redisson.getMapCache(name, codec);
        map.setMaxSize(config.getMaxSize());
        return new RedissonShiroCache<>(map, config, allowNullValues);
    }
}
