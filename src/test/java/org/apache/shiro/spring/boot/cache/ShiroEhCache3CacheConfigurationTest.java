package org.apache.shiro.spring.boot.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.ehcache.Status;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShiroEhCache3CacheConfigurationTest {

    @Test
    void sharesInjectedRegionsAndUsesXmlExpiryForNewRegions(@TempDir Path temporary) throws IOException {
        Path xml = writeTemplate(temporary);
        try (org.ehcache.CacheManager external = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("existing", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        Object.class, Object.class, ResourcePoolsBuilder.heap(10)))
                .build(true)) {
            org.ehcache.Cache<Object, Object> nativeCache = external.getCache("existing", Object.class, Object.class);
            nativeCache.put("native", "shared");
            try (AnnotationConfigApplicationContext context = context(xml.toUri().toString())) {
                context.registerBean(org.ehcache.CacheManager.class, () -> external,
                        definition -> definition.setDestroyMethodName(""));
                context.refresh();
                CacheManager manager = context.getBean(CacheManager.class);
                Cache<String, String> existing = manager.getCache("existing");
                assertEquals("shared", existing.get("native"));
                existing.put("from-shiro", "same cache");
                assertEquals("same cache", nativeCache.get("from-shiro"));
                manager.getCache("dynamic").put("entry", "value");
                var expiry = external.getCache("dynamic", Object.class, Object.class)
                        .getRuntimeConfiguration().getExpiryPolicy();
                assertEquals(Duration.ofSeconds(7), expiry.getExpiryForCreation("entry", "value"));
            }
            assertEquals(Status.AVAILABLE, external.getStatus());
            assertEquals("same cache", nativeCache.get("from-shiro"));
        }
    }

    @Test
    void closesAnXmlOwnedManagerWhenTheContextCloses(@TempDir Path temporary) throws IOException {
        Cache<String, String> owned;
        try (AnnotationConfigApplicationContext context = context("url:" + writeTemplate(temporary).toUri())) {
            context.refresh();
            owned = context.getBean(CacheManager.class).getCache("sessions");
            owned.put("key", "value");
            assertEquals("value", owned.get("key"));
        }
        assertThrows(IllegalStateException.class, () -> owned.get("key"));
    }

    private static AnnotationConfigApplicationContext context(String location) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("shiro-test", Map.of(
                "shiro.cache.type", "ehcache3",
                "shiro.cache.ehcache3.cache-manager-config-file", location)));
        context.register(ShiroEhCache3CacheConfiguration.class);
        return context;
    }

    private static Path writeTemplate(Path directory) throws IOException {
        return Files.writeString(directory.resolve("ehcache.xml"), """
                <config xmlns="http://www.ehcache.org/v3">
                  <cache-template name="defaultCacheConfiguration">
                    <expiry><ttl unit="seconds">7</ttl></expiry>
                    <heap unit="entries">10</heap>
                  </cache-template>
                </config>
                """);
    }
}
