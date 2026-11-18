package org.apache.shiro.spring.boot.cache;

import java.util.Map;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiroSpringCacheConfigurationTest {

    @Test
    void springModeSharesTheConfiguredCacheRegion() {
        try (AnnotationConfigApplicationContext context = context("spring")) {
            ConcurrentMapCacheManager spring = context.getBean(ConcurrentMapCacheManager.class);
            Cache<String, String> shiro = context.getBean(CacheManager.class).getCache("sessions");

            spring.getCache("sessions").put("from-spring", "original");
            assertEquals("original", shiro.get("from-spring"));

            shiro.put("from-shiro", "delegated");
            assertEquals("delegated", spring.getCache("sessions").get("from-shiro", String.class));
        }
    }

    @Test
    void otherModesDoNotRegisterTheSpringDelegate() {
        try (AnnotationConfigApplicationContext context = context("ehcache")) {
            assertTrue(context.getBeansOfType(CacheManager.class).isEmpty());
        }
    }

    private static AnnotationConfigApplicationContext context(String type) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("shiro-test", Map.of("shiro.cache.type", type)));
        context.registerBean(ConcurrentMapCacheManager.class, () -> new ConcurrentMapCacheManager("sessions"));
        context.register(ShiroSpringCacheConfiguration.class);
        context.refresh();
        return context;
    }
}
