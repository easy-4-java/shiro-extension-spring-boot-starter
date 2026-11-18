package org.apache.shiro.spring.boot.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShiroRedissonCacheConfigurationTest {

    @Test
    void usesFileExpiryWhenShiroInitializesTheManager(@TempDir Path temporary) throws IOException {
        Path yaml = Files.writeString(temporary.resolve("cache settings.yml"), """
                sessions:
                  ttl: 5000
                  maxIdleTime: 2000
                  maxSize: 10
                """);
        assertRegionSettings(Map.of(
                "shiro.cache.redisson.config-location", "file:" + yaml,
                "shiro.cache.redisson.config-map.sessions.ttl", "9999"));
    }

    @Test
    void usesInlineExpiryWithoutAFile() {
        assertRegionSettings(Map.of(
                "shiro.cache.redisson.config-map.sessions.ttl", "5000",
                "shiro.cache.redisson.config-map.sessions.max-idle-time", "2000",
                "shiro.cache.redisson.config-map.sessions.max-size", "10"));
    }

    @SuppressWarnings("unchecked")
    private static void assertRegionSettings(Map<String, Object> properties) {
        RedissonClient redisson = mock(RedissonClient.class);
        RMapCache<String, String> region = mock(RMapCache.class);
        when(redisson.<String, String>getMapCache(eq("sessions"), any(Codec.class))).thenReturn(region);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "provider", Map.of("shiro.cache.type", "redisson")));
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("redisson", properties));
            context.registerBean(LifecycleBeanPostProcessor.class, () -> new LifecycleBeanPostProcessor());
            context.registerBean(RedissonClient.class, () -> redisson);
            context.register(ShiroRedissonCacheConfiguration.class);
            context.refresh();
            context.getBean(CacheManager.class).getCache("sessions").put("key", "value");
            verify(region).setMaxSize(10);
            verify(region).put("key", "value", 5000L, TimeUnit.MILLISECONDS, 2000L, TimeUnit.MILLISECONDS);
        }
    }
}
