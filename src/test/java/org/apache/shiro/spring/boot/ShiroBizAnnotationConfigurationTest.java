package org.apache.shiro.spring.boot;

import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ShiroBizAnnotationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShiroBizAnnotationProcessorAutoConfiguration.class))
            .withBean("lifecycleBeanPostProcessor", LifecycleBeanPostProcessor.class, LifecycleBeanPostProcessor::new);

    @Test
    void enablesOnlyTheConfiguredAdvisorOptions() {
        contextRunner.withPropertyValues("shiro.annotations.enabled=true", "shiro.annotations.proxy-target-class=false",
                        "shiro.annotations.expose-proxy=false", "shiro.annotations.opaque=true",
                        "shiro.annotations.frozen=true", "shiro.annotations.optimize=true",
                        "shiro.annotations.use-prefix=true", "shiro.annotations.advisor-bean-name-prefix=security.")
                .run(context -> {
                    assertThat(context).hasSingleBean(DefaultAdvisorAutoProxyCreator.class);
                    DefaultAdvisorAutoProxyCreator creator = context.getBean(DefaultAdvisorAutoProxyCreator.class);
                    assertThat(creator.isProxyTargetClass()).isFalse();
                    assertThat(creator.isExposeProxy()).isFalse();
                    assertThat(creator.isOpaque()).isTrue();
                    assertThat(creator.isFrozen()).isTrue();
                    assertThat(creator.isOptimize()).isTrue();
                    assertThat(creator.isUsePrefix()).isTrue();
                    assertThat(creator.getAdvisorBeanNamePrefix()).isEqualTo("security.");
                    assertThat(context.getBean(ShiroBizAnnotationProperties.class).isEnabled()).isTrue();
                });
    }

    @Test
    void disablesTheAdvisorByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DefaultAdvisorAutoProxyCreator.class));
    }
}
