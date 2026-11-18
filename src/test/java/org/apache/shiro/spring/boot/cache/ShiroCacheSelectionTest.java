package org.apache.shiro.spring.boot.cache;

import com.github.streamone.shiro.cache.RedissonShiroCacheManager;
import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.hazelcast.cache.HazelcastCacheManager;
import org.crazycake.shiro.IRedisManager;
import org.crazycake.shiro.RedisCacheManager;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShiroCacheSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShiroSpringCacheConfiguration.class,
                    ShiroHazelcastCacheConfiguration.class, ShiroRedisCacheConfiguration.class,
                    ShiroRedissonCacheConfiguration.class, ShiroEhCache2CacheConfiguration.class,
                    ShiroEhCache3CacheConfiguration.class));

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
    @Test
    void redisAdapterUsesConfiguredExpiryPrefixAndPrincipalField() {
        IRedisManager redis = mock(IRedisManager.class);
        contextRunner.withBean(IRedisManager.class, () -> redis)
                .withBean(JedisPool.class, () -> mock(JedisPool.class))
                .withPropertyValues("shiro.cache.type=redis", "shiro.cache.redis.expire=420",
                        "shiro.cache.redis.key-prefix=example:", "shiro.cache.redis.principal-id-field-name=employeeId")
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    RedisCacheManager manager = context.getBean(RedisCacheManager.class);
                    assertThat(context).hasSingleBean(IRedisManager.class);
                    assertThat(manager.getRedisManager()).isSameAs(redis);
                    assertThat(manager.getExpire()).isEqualTo(420);
                    assertThat(manager.getKeyPrefix()).isEqualTo("example:");
                    assertThat(manager.getPrincipalIdFieldName()).isEqualTo("employeeId");
                });
    }

    @Test
    void redisAdaptsTheApplicationJedisPoolWhenNoCustomManagerExists() {
        JedisPool pool = mock(JedisPool.class);
        Jedis jedis = mock(Jedis.class);
        when(pool.getResource()).thenReturn(jedis);
        byte[] key = {1};
        byte[] value = {2};
        byte[] match = {3};
        byte[] cursor = {'5'};
        byte[] first = {4};
        byte[] second = {5};
        when(jedis.get(key)).thenReturn(value);
        when(jedis.scan(eq(ScanParams.SCAN_POINTER_START_BINARY), any(ScanParams.class)))
                .thenReturn(new ScanResult<>(cursor, List.of(first)));
        when(jedis.scan(eq(cursor), any(ScanParams.class)))
                .thenReturn(new ScanResult<>(ScanParams.SCAN_POINTER_START_BINARY, List.of(second)));

        contextRunner.withBean(JedisPool.class, () -> pool)
                .withPropertyValues("shiro.cache.type=redis")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IRedisManager.class);
                    IRedisManager manager = context.getBean(IRedisManager.class);
                    assertThat(context.getBean(RedisCacheManager.class).getRedisManager()).isSameAs(manager);
                    assertThat(manager.get(key)).isSameAs(value);
                    assertThat(manager.set(key, value, 30)).isSameAs(value);
                    assertThat(manager.set(key, value, 0)).isSameAs(value);
                    manager.del(key);
                    assertThat(manager.dbSize(match)).isEqualTo(2L);
                    assertThat(manager.keys(match)).containsExactlyInAnyOrder(first, second);
                    assertThat(manager.get(null)).isNull();
                    assertThat(manager.set(null, value, 1)).isNull();
                    manager.del(null);
                });

        verify(jedis).set(key, value, SetParams.setParams().ex(30));
        verify(jedis).set(key, value);
        verify(jedis).del(key);
        verify(jedis, times(6)).close();
    }

    @Test
    void redissonAdapterBindsNullValuePolicyAndUsesTheProvidedClient() {
        RedissonClient redisson = mock(RedissonClient.class);
        contextRunner.withBean(RedissonClient.class, () -> redisson)
                .withPropertyValues("shiro.cache.type=redisson", "shiro.cache.redisson.allow-null-values=false",
                        "shiro.cache.redisson.config-location=classpath:cache-policy.yaml",
                        "shiro.cache.redisson.config-map.sessions.ttl=45000")
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    RedissonShiroCacheManager manager = context.getBean(RedissonShiroCacheManager.class);
                    assertThat(manager.getRedisson()).isSameAs(redisson);
                    assertThat(manager.isAllowNullValues()).isFalse();
                    assertThat(manager.getConfigLocation()).isEqualTo("classpath:cache-policy.yaml");
                    assertThat(manager.getCodec()).isInstanceOf(StringCodec.class);
                    ShiroRedissonCacheProperties properties = context.getBean(ShiroRedissonCacheProperties.class);
                    assertThat(properties.getConfigMap().get("sessions").getTTL()).isEqualTo(45_000L);
                });
    }

    @Test
    void ehcache2AdapterUsesItsOwnConfigAndDelegatesToAnExistingManager() {
        net.sf.ehcache.CacheManager cache = mock(net.sf.ehcache.CacheManager.class);
        contextRunner.withBean(net.sf.ehcache.CacheManager.class, () -> cache)
                .withPropertyValues("shiro.cache.type=ehcache",
                        "shiro.cache.ehcache2.cache-manager-config-file=classpath:ehcache.xml")
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    org.apache.shiro.cache.ehcache.EhCacheManager manager =
                            context.getBean(org.apache.shiro.cache.ehcache.EhCacheManager.class);
                    assertThat(manager.getCacheManager()).isSameAs(cache);
                    assertThat(manager.getCacheManagerConfigFile()).isEqualTo("classpath:ehcache.xml");
                });
    }

    @Test
    void ehcache3AdapterUsesItsOwnConfigAndDelegatesToAnExistingManager() {
        org.ehcache.CacheManager cache = mock(org.ehcache.CacheManager.class);
        contextRunner.withBean(org.ehcache.CacheManager.class, () -> cache)
                .withPropertyValues("shiro.cache.type=ehcache3",
                        "shiro.cache.ehcache3.cache-manager-config-file=classpath:ehcache3.xml")
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    org.ehcache.integrations.shiro.EhcacheShiroManager manager =
                            context.getBean(org.ehcache.integrations.shiro.EhcacheShiroManager.class);
                    assertThat(manager.getCacheManager()).isSameAs(cache);
                    assertThat(manager.getCacheManagerConfigFile()).isEqualTo("classpath:ehcache3.xml");
                });
    }

}
