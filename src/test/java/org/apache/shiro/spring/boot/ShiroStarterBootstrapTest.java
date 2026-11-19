package org.apache.shiro.spring.boot;

import org.apache.shiro.cache.MemoryConstrainedCacheManager;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.spring.boot.actuate.ShiroEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ShiroStarterBootstrapTest {

    @Test
    void autoConfiguresShiroBizAndTheSelectedCache() {
        new WebApplicationContextRunner()
                .withUserConfiguration(TestApplication.class)
                .withPropertyValues("shiro.cache.type=memory")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ShiroBizProperties.class);
                    assertThat(context.getBean("shiroCacheManager")).isInstanceOf(MemoryConstrainedCacheManager.class);
                });
    }

    @Test
    void allowsSpringCacheWhenHazelcastIsOnTheClasspath() {
        new WebApplicationContextRunner()
                .withUserConfiguration(TestApplication.class)
                .withBean(org.springframework.cache.CacheManager.class, () -> new ConcurrentMapCacheManager("mapped"))
                .withPropertyValues("shiro.cache.type=spring")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(org.apache.shiro.cache.CacheManager.class).getCache("mapped")).isNotNull();
                });
    }

    @Test
    void exposesTheShiroActuatorEndpointOnlyWhenSelected() {
        var runner = new WebApplicationContextRunner()
                .withUserConfiguration(TestApplication.class)
                .withPropertyValues("shiro.cache.type=memory");
        runner.withPropertyValues("management.endpoints.web.exposure.include=health")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ShiroEndpoint.class);
                });
        runner.withPropertyValues("management.endpoints.web.exposure.include=shiro")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ShiroEndpoint.class).shiroSessions().getSessions()).isEmpty();
                    assertThat(context.getBean(WebEndpointsSupplier.class).getEndpoints())
                            .anyMatch(endpoint -> endpoint.getEndpointId().toString().equals("shiro"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        SimpleAccountRealm shiroRealm() {
            return new SimpleAccountRealm();
        }
    }
}
