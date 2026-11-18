package org.apache.shiro.spring.boot;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import org.apache.shiro.authc.ExcessiveAttemptsException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.biz.authc.credential.CredentialRetryStore;
import org.apache.shiro.biz.authc.credential.CredentialRetryStore.Attempt;
import org.apache.shiro.biz.authc.credential.DefaultCredentialsMatcher;
import org.apache.shiro.biz.authc.credential.spring.CredentialRetryStoreAutoConfiguration;
import org.apache.shiro.biz.cache.ehcache.Ehcache2CredentialRetryStore;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShiroEhcacheRetryCutoverTest {

    @Test
    void bootManagedRealmKeepsTheLegacyCountAndExpirationOnCutover() {
        CacheManager cacheManager = new CacheManager(new Configuration().name("retry-cutover-" + UUID.randomUUID()));
        try {
            Cache cache = new Cache(new CacheConfiguration("login", 100)
                .timeToLiveSeconds(60).timeToIdleSeconds(120).copyOnRead(true).copyOnWrite(true));
            cacheManager.addCache(cache);
            long createdAt = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(5);
            cache.put(new Element("alice", new AtomicInteger(3), 1, createdAt, createdAt, 0,
                false, 60, 120, createdAt));
            long originalDeadline = cache.getQuiet("alice").getExpirationTime();
            CredentialRetryStore store = new Ehcache2CredentialRetryStore<>(cache, Object::toString);
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
                    Element adopted = cache.getQuiet("alice");
                    Attempt first = (Attempt) adopted.getObjectValue();
                    assertThat(first.getCount()).isEqualTo(4);
                    assertThat(adopted.getExpirationTime()).isEqualTo(originalDeadline);

                    assertThatThrownBy(() -> matcher.doCredentialsMatch(token, account))
                        .isInstanceOf(ExcessiveAttemptsException.class);
                    Element limited = cache.getQuiet("alice");
                    Attempt last = (Attempt) limited.getObjectValue();
                    assertThat(last.getCount()).isEqualTo(5);
                    assertThat(last.getWindowId()).isEqualTo(first.getWindowId());
                    assertThat(limited.getCreationTime()).isEqualTo(createdAt);
                    assertThat(limited.getExpirationTime()).isEqualTo(originalDeadline);
                });
        } finally {
            cacheManager.shutdown();
        }
    }
}
