package org.apache.shiro.spring.boot.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import net.sf.ehcache.Element;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class ShiroEhCache2CacheConfigurationTest {

    @Test
    void readsTheConfiguredRegionAndSharesItsEntries(@TempDir Path directory) throws IOException {
        Path xml = Files.writeString(directory.resolve("ehcache.xml"), """
                <ehcache name="shiro-ehcache2-test" updateCheck="false">
                    <defaultCache maxEntriesLocalHeap="50" timeToLiveSeconds="31" />
                    <cache name="sessions" maxEntriesLocalHeap="10" timeToLiveSeconds="17" />
                </ehcache>
                """);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(LifecycleBeanPostProcessor.class, () -> new LifecycleBeanPostProcessor());
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("ehcache2", Map.of(
                    "shiro.cache.type", "ehcache",
                    "shiro.cache.ehcache2.cache-manager-config-file", "url:" + xml.toUri())));
            context.register(ShiroEhCache2CacheConfiguration.class);
            context.refresh();

            var manager = (EhCacheManager) context.getBean(CacheManager.class);
            Cache<String, String> cache = manager.getCache("sessions");
            var nativeCache = manager.getCacheManager().getCache("sessions");
            assertThat(nativeCache.getCacheConfiguration().getTimeToLiveSeconds()).isEqualTo(17);
            nativeCache.put(new Element("native", "from-ehcache"));
            assertThat(cache.get("native")).isEqualTo("from-ehcache");
            cache.put("shiro", "from-shiro");
            assertThat(nativeCache.get("shiro").getObjectValue()).isEqualTo("from-shiro");
        }
    }
}
