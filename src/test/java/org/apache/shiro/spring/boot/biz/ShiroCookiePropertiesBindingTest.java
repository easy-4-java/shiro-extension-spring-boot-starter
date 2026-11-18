package org.apache.shiro.spring.boot.biz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ShiroCookiePropertiesBindingTest {

    @Test
    void supportsTheLegacySessionAndRememberMeConfigurationKeys() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConsumerConfiguration.class)
                .withPropertyValues(
                        "shiro.sessionManager.sessionIdCookieEnabled=false",
                        "shiro.sessionManager.sessionIdUrlRewritingEnabled=false",
                        "shiro.sessionManager.deleteInvalidSessions=false",
                        "shiro.sessionManager.cookie.name=application-session",
                        "shiro.sessionManager.cookie.max-age=600",
                        "shiro.sessionManager.cookie.domain=example.invalid",
                        "shiro.sessionManager.cookie.path=/protected",
                        "shiro.sessionManager.cookie.secure=true",
                        "shiro.rememberMeManager.cookie.name=application-remember",
                        "shiro.rememberMeManager.cookie.max-age=1200",
                        "shiro.rememberMeManager.cookie.domain=example.invalid",
                        "shiro.rememberMeManager.cookie.path=/accounts",
                        "shiro.rememberMeManager.cookie.secure=true",
                        "shiro.rememberMeManager.cookie.secret-key=example-configured-value")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var sessions = context.getBean(ShiroSessionManagerProperties.class);
                    assertThat(sessions.isSessionIdCookieEnabled()).isFalse();
                    assertThat(sessions.isSessionIdUrlRewritingEnabled()).isFalse();
                    assertThat(sessions.isDeleteInvalidSessions()).isFalse();
                    var sessionCookie = context.getBean(ShiroSessionManagerCookieProperties.class);
                    assertThat(sessionCookie.getName()).isEqualTo("application-session");
                    assertThat(sessionCookie.getMaxAge()).isEqualTo(600);
                    assertThat(sessionCookie.getDomain()).isEqualTo("example.invalid");
                    assertThat(sessionCookie.getPath()).isEqualTo("/protected");
                    assertThat(sessionCookie.isSecure()).isTrue();
                    var rememberCookie = context.getBean(ShiroRememberMeManagerCookieProperties.class);
                    assertThat(rememberCookie.getName()).isEqualTo("application-remember");
                    assertThat(rememberCookie.getMaxAge()).isEqualTo(1200);
                    assertThat(rememberCookie.getDomain()).isEqualTo("example.invalid");
                    assertThat(rememberCookie.getPath()).isEqualTo("/accounts");
                    assertThat(rememberCookie.isSecure()).isTrue();
                    assertThat(rememberCookie.getSecretKey()).isEqualTo("example-configured-value");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            ShiroSessionManagerProperties.class,
            ShiroSessionManagerCookieProperties.class,
            ShiroRememberMeManagerCookieProperties.class
    })
    static class ConsumerConfiguration {
    }
}
