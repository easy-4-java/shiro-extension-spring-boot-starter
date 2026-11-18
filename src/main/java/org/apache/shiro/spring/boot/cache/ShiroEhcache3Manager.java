package org.apache.shiro.spring.boot.cache;

import java.io.IOException;
import java.util.Objects;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheException;
import org.apache.shiro.cache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.integrations.shiro.EhcacheShiro;
import org.ehcache.xml.XmlConfiguration;
import org.springframework.core.io.DefaultResourceLoader;

/** Manages Ehcache 3 lifecycle independently of the Shiro 1 integration interfaces. */
final class ShiroEhcache3Manager implements CacheManager, AutoCloseable {

    private static final String DEFAULT_TEMPLATE = "defaultCacheConfiguration";
    private static final String URL_PREFIX = "url:";

    private final boolean ownsManager;
    private final String configLocation;
    private org.ehcache.CacheManager manager;
    private XmlConfiguration configuration;

    ShiroEhcache3Manager(org.ehcache.CacheManager manager, String configLocation) {
        this.manager = manager;
        this.ownsManager = Objects.isNull(manager);
        this.configLocation = configLocation;
    }

    @Override
    public synchronized <K, V> Cache<K, V> getCache(String name) throws CacheException {
        try {
            if (Objects.isNull(manager)) {
                org.ehcache.CacheManager created = CacheManagerBuilder.newCacheManager(configuration());
                created.init();
                manager = created;
            }
            org.ehcache.Cache<Object, Object> cache = manager.getCache(name, Object.class, Object.class);
            if (Objects.isNull(cache)) {
                CacheConfigurationBuilder<Object, Object> template = configuration()
                        .newCacheConfigurationBuilderFromTemplate(DEFAULT_TEMPLATE, Object.class, Object.class);
                if (Objects.isNull(template)) {
                    throw new CacheException("Ehcache XML is missing the defaultCacheConfiguration template");
                }
                cache = manager.createCache(name, template.build());
            }
            return new EhcacheShiro<>(cache);
        } catch (IOException | ReflectiveOperationException ex) {
            throw new CacheException("Unable to configure the Ehcache 3 region", ex);
        }
    }

    private XmlConfiguration configuration() throws IOException {
        if (Objects.isNull(configuration)) {
            String location = configLocation.startsWith(URL_PREFIX)
                    ? configLocation.substring(URL_PREFIX.length()) : configLocation;
            configuration = new XmlConfiguration(new DefaultResourceLoader().getResource(location).getURL());
        }
        return configuration;
    }

    @Override
    public synchronized void close() {
        if (ownsManager && Objects.nonNull(manager)) {
            manager.close();
            manager = null;
        }
    }
}
