package org.apache.shiro.spring.boot.cache;

import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.hazelcast.cache.HazelcastCacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class ShiroCacheSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShiroSpringCacheConfiguration.class,
                    ShiroHazelcastCacheConfiguration.class));

    @Test
    void springCacheUsesTheApplicationsCacheManager() {
        ConcurrentMapCacheManager springCache = new ConcurrentMapCacheManager("sessions");
        contextRunner.withPropertyValues("shiro.cache.type=spring")
                .withBean(org.springframework.cache.CacheManager.class, () -> springCache)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).doesNotHaveBean(ShiroHazelcastCacheProperties.class);
                    Cache<String, String> sessions = context.getBean(CacheManager.class).getCache("sessions");

                    sessions.put("key", "value");

                    assertThat(springCache.getCache("sessions").get("key", String.class)).isEqualTo("value");
                });
    }

    @Test
    void hazelcastCacheIsSelectedByItsOwnType() {
        contextRunner.withPropertyValues("shiro.cache.type=hazelcast", "shiro.cache.hazelcast.cluster-name=shiro-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class)).isInstanceOf(HazelcastCacheManager.class);
                    assertThat(context.getBean(ShiroHazelcastCacheProperties.class).getClusterName()).isEqualTo("shiro-test");
                });
    }
}
