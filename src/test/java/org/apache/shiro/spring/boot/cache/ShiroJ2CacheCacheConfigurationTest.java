package org.apache.shiro.spring.boot.cache;

import java.util.Map;

import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShiroJ2CacheCacheConfigurationTest {

    @Test
    void sharesRegionsWithTheConfiguredChannelAndClosesItAtShutdown() {
        CacheChannel channel = mock(CacheChannel.class);
        when(channel.get("sessions", "id")).thenReturn(new CacheObject("sessions", "id", CacheObject.LEVEL_1, "value"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "provider", Map.of("shiro.cache.type", "j2cache")));
            context.registerBean(CacheChannel.class, () -> channel, bean -> bean.setDestroyMethodName(""));
            context.register(ShiroJ2CacheCacheConfiguration.class);
            context.refresh();
            CacheManager manager = context.getBean(CacheManager.class);
            Cache<String, String> cache = manager.getCache("sessions");
            assertSame(cache, manager.getCache("sessions"));
            assertEquals("value", cache.get("id"));
            cache.put("new-id", "new-value");
            verify(channel).set("sessions", "new-id", "new-value");
        }
        verify(channel).close();
    }
}
