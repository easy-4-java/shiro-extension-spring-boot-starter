package org.apache.shiro.spring.boot.cache;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.crazycake.shiro.IRedisManager;
import org.crazycake.shiro.exception.SerializationException;
import org.crazycake.shiro.serializer.ObjectSerializer;
import org.crazycake.shiro.serializer.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShiroRedisCacheConfigurationTest {

    @Test
    void readsExistingRedisEntriesAndPreservesTheWriteExpiry() throws SerializationException {
        IRedisManager redis = mock(IRedisManager.class);
        var keys = new StringSerializer();
        var values = new ObjectSerializer();
        byte[] storedKey = keys.serialize("tenant:cache:sessions:id");
        byte[] storedValue = values.serialize("old-value");
        when(redis.keys(keys.serialize("tenant:cache:sessions:*"))).thenReturn(Set.of(storedKey));
        when(redis.get(storedKey)).thenReturn(storedValue);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("cache", Map.of(
                    "shiro.cache.type", "redis",
                    "shiro.cache.redis.key-prefix", "tenant:cache:",
                    "shiro.cache.redis.expire", "120")));
            context.registerBean(IRedisManager.class, () -> redis);
            context.register(ShiroRedisCacheConfiguration.class);
            context.refresh();
            CacheManager manager = context.getBean(CacheManager.class);
            Cache<String, String> cache = manager.getCache("sessions");
            assertSame(cache, manager.getCache("sessions"));
            assertEquals("old-value", cache.get("id"));
            assertEquals(Set.of("tenant:cache:sessions:id"), cache.keys());
            assertEquals(List.of("old-value"), cache.values());
            cache.put("id", "new-value");
            verify(redis).set(storedKey, values.serialize("new-value"), 120);
        }
    }
}
