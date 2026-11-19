package org.apache.shiro.spring.boot.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.oschina.j2cache.CacheChannel;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.cache.j2cache.J2CacheWrapper;

/** 使用已配置的 J2Cache 通道创建 Shiro 缓存区域。 */
final class ShiroJ2CacheManager implements CacheManager, AutoCloseable {

    private final CacheChannel channel;
    private final Map<String, Cache<?, ?>> regions = new ConcurrentHashMap<>();

    ShiroJ2CacheManager(CacheChannel channel) {
        this.channel = channel;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name) {
        return (Cache<K, V>) regions.computeIfAbsent(name, region -> new J2CacheWrapper<>(region, channel));
    }

    @Override
    public void close() {
        channel.close();
    }
}
