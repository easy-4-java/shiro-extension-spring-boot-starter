/*
 * Copyright (c) 2018, hiwepy (https://github.com/hiwepy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.shiro.spring.boot;

import org.apache.shiro.spring.boot.autoconfigure.ShiroAnnotationProcessorAutoConfiguration;
import org.apache.shiro.spring.config.AbstractShiroAnnotationProcessorConfiguration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@AutoConfigureAfter(ShiroAnnotationProcessorAutoConfiguration.class)
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = ShiroBizAnnotationProperties.PREFIX, value = "enabled", havingValue = "true")
@EnableConfigurationProperties(ShiroBizAnnotationProperties.class)
public class ShiroBizAnnotationProcessorAutoConfiguration extends AbstractShiroAnnotationProcessorConfiguration {

    private static final String SHIRO_PROXY_CREATOR = "defaultAdvisorAutoProxyCreator";

    @Bean
    static BeanFactoryPostProcessor shiroAnnotationProxySettings(Environment environment) {
        return beanFactory -> {
            if (!beanFactory.containsBeanDefinition(SHIRO_PROXY_CREATOR)) {
                return;
            }
            ShiroBizAnnotationProperties settings = Binder.get(environment)
                    .bind(ShiroBizAnnotationProperties.PREFIX, ShiroBizAnnotationProperties.class)
                    .orElseGet(ShiroBizAnnotationProperties::new);
            var properties = beanFactory.getBeanDefinition(SHIRO_PROXY_CREATOR).getPropertyValues();
            properties.add("proxyTargetClass", settings.isProxyTargetClass());
            properties.add("exposeProxy", settings.isExposeProxy());
            properties.add("frozen", settings.isFrozen());
            properties.add("opaque", settings.isOpaque());
            if (settings.isUsePrefix() && StringUtils.hasText(settings.getAdvisorBeanNamePrefix())) {
                properties.add("usePrefix", true);
                properties.add("advisorBeanNamePrefix", settings.getAdvisorBeanNamePrefix());
            }
        };
    }
}
