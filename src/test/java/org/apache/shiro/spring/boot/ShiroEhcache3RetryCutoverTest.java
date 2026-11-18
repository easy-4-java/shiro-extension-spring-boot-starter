package org.apache.shiro.spring.boot;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.shiro.authc.ExcessiveAttemptsException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.biz.authc.credential.CredentialRetryStore;
import org.apache.shiro.biz.authc.credential.CredentialRetryStore.Attempt;
import org.apache.shiro.biz.authc.credential.DefaultCredentialsMatcher;
import org.apache.shiro.biz.authc.credential.spring.CredentialRetryStoreAutoConfiguration;
import org.apache.shiro.biz.cache.ehcache.Ehcache3CredentialRetryStore;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.impl.internal.TimeSourceConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShiroEhcache3RetryCutoverTest {

    @Test
    void bootManagedRealmHonorsTheMigratedOffHeapRetryWindow() {
        AtomicLong clock = new AtomicLong(10_000);
        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
            .using(new TimeSourceConfiguration(clock::get))
            .withCache("login", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    String.class, Serializable.class, ResourcePoolsBuilder.newResourcePoolsBuilder()
                        .offheap(2, MemoryUnit.MB))
                .withExpiry(Ehcache3CredentialRetryStore.fixedWindowExpiry(Duration.ofSeconds(3))))
            .build(true)) {
            Cache<String, Serializable> cache = manager.getCache("login", String.class, Serializable.class);
            cache.put("alice", new AtomicInteger(3));
            Ehcache3CredentialRetryStore<String> store = new Ehcache3CredentialRetryStore<>(cache, Object::toString);
            clock.set(12_000);
            assertThat(store.migrateLegacyWindow("alice")).isTrue();
            assertThat(store.migrateLegacyWindow("alice")).isFalse();

            DefaultCredentialsMatcher matcher = new DefaultCredentialsMatcher();
            matcher.setCredentialsRetryCacheName("login");
            matcher.setRetryLimit(5);
            new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CredentialRetryStoreAutoConfiguration.class))
                .withPropertyValues("shiro.enabled=true")
                .withBean("shiro.credential-retry.login", CredentialRetryStore.class, () -> store)
                .withBean("retryRealm", SimpleAccountRealm.class, () -> {
                    SimpleAccountRealm realm = new SimpleAccountRealm();
                    realm.setCredentialsMatcher(matcher);
                    return realm;
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(matcher.getCredentialRetryStore()).isSameAs(store);
                    UsernamePasswordToken token = new UsernamePasswordToken("alice", "wrong");
                    SimpleAuthenticationInfo account = new SimpleAuthenticationInfo("alice", "correct", "retryRealm");

                    assertThat(matcher.doCredentialsMatch(token, account)).isFalse();
                    Attempt migrated = (Attempt) cache.get("alice");
                    assertThat(migrated.getCount()).isEqualTo(4);
                    assertThatThrownBy(() -> matcher.doCredentialsMatch(token, account))
                        .isInstanceOf(ExcessiveAttemptsException.class);

                    clock.set(13_001);
                    assertThat(cache.get("alice")).isNull();
                    assertThat(matcher.doCredentialsMatch(token, account)).isFalse();
                    Attempt next = (Attempt) cache.get("alice");
                    assertThat(next.getCount()).isEqualTo(1);
                    assertThat(next.getWindowId()).isNotEqualTo(migrated.getWindowId());
                });
        }
    }
}
