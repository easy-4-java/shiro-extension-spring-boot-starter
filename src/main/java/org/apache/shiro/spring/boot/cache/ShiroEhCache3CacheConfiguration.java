package org.apache.shiro.spring.boot.cache;

import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.spring.config.web.autoconfigure.ShiroWebAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.AbstractCachingConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureAfter(AbstractCachingConfiguration.class)
@AutoConfigureBefore(ShiroWebAutoConfiguration.class)
@ConditionalOnClass(org.ehcache.CacheManager.class)
@ConditionalOnProperty(prefix = ShiroCacheProperties.PREFIX, value = "type", havingValue = "ehcache3")
@EnableConfigurationProperties({ ShiroEhCache3CacheProperties.class })
public class ShiroEhCache3CacheConfiguration {

	@Bean(destroyMethod = "close")
	public CacheManager shiroCacheManager(
			ShiroEhCache3CacheProperties properties,
			@Autowired(required = false) org.ehcache.CacheManager cacheManager) {
		return new ShiroEhcache3Manager(cacheManager, properties.getCacheManagerConfigFile());
	}

}
