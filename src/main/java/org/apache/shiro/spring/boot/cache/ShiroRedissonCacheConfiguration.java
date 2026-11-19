package org.apache.shiro.spring.boot.cache;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.spring.config.web.autoconfigure.ShiroWebAutoConfiguration;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.spring.cache.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.AbstractCachingConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.github.streamone.shiro.cache.RedissonShiroCache;

@Configuration
@AutoConfigureAfter(AbstractCachingConfiguration.class)
@AutoConfigureBefore(ShiroWebAutoConfiguration.class)
@ConditionalOnClass({RedissonClient.class, RedissonShiroCache.class})
@ConditionalOnProperty(prefix = ShiroCacheProperties.PREFIX, value = "type", havingValue = "redisson")
@EnableConfigurationProperties({ ShiroRedissonCacheProperties.class })
public class ShiroRedissonCacheConfiguration {

	@Bean
	@ConditionalOnMissingBean
	protected Codec codec() {
		return new StringCodec();
	}

	@Bean
	public CacheManager shiroCacheManager(ShiroRedissonCacheProperties properties, RedissonClient redisson,
			@Autowired(required = false) Codec codec) {
		String location = properties.getConfigLocation();
		Map<String, ? extends CacheConfig> configs = Objects.isNull(location) ? properties.getConfigMap()
				: loadRegionConfigurations(location);
		return new ShiroRedissonCacheManager(redisson, codec, configs, properties.isAllowNullValues());
	}

	private static Map<String, ? extends CacheConfig> loadRegionConfigurations(String location) {
		Resource source;
		if (location.startsWith("classpath:")) {
			source = new DefaultResourceLoader().getResource(location);
		} else if (location.startsWith("url:")) {
			source = new DefaultResourceLoader().getResource(location.substring("url:".length()));
		} else {
			String file = location.startsWith("file:") ? location.substring("file:".length()) : location;
			source = new FileSystemResource(file);
		}

		try (InputStream input = source.getInputStream()) {
			return CacheConfig.fromJSON(input);
		} catch (IOException jsonFailure) {
			try (InputStream input = source.getInputStream()) {
				return CacheConfig.fromYAML(input);
			} catch (IOException yamlFailure) {
				yamlFailure.addSuppressed(jsonFailure);
				throw new IllegalArgumentException("Could not parse cache configuration at [" + location + "]", yamlFailure);
			}
		}
	}

}
