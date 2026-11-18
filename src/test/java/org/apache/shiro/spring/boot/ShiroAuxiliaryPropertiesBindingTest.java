package org.apache.shiro.spring.boot;

import java.util.Collections;

import org.apache.shiro.spring.boot.biz.ShiroRememberMeManagerCookieProperties;
import org.apache.shiro.spring.boot.biz.ShiroSessionManagerCookieProperties;
import org.apache.shiro.spring.boot.biz.ShiroSessionManagerProperties;
import org.apache.shiro.spring.boot.captcha.ShiroKaptchaProperties;
import org.apache.shiro.spring.boot.template.method.CaptchaEnabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class ShiroAuxiliaryPropertiesBindingTest {

    @Test
    void bindsExistingCamelCaseKeysAndCanonicalCookieKeys() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("shiro.sessionManager.session-id-cookie-enabled=false",
                        "shiro.sessionManager.session-id-url-rewriting-enabled=false",
                        "shiro.sessionManager.delete-invalid-sessions=false",
                        "shiro.sessionManager.cookie.name=APPLICATION_SESSION",
                        "shiro.sessionManager.cookie.max-age=600",
                        "shiro.sessionManager.cookie.domain=example.test",
                        "shiro.sessionManager.cookie.path=/app",
                        "shiro.sessionManager.cookie.secure=true",
                        "shiro.remember-me-manager.cookie.name=APPLICATION_REMEMBER",
                        "shiro.remember-me-manager.cookie.max-age=1200",
                        "shiro.remember-me-manager.cookie.domain=example.test",
                        "shiro.remember-me-manager.cookie.path=/app",
                        "shiro.remember-me-manager.cookie.secure=true",
                        "shiro.remember-me-manager.cookie.secret-key=fixture-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ShiroSessionManagerProperties sessions = context.getBean(ShiroSessionManagerProperties.class);
                    assertThat(sessions.isSessionIdCookieEnabled()).isFalse();
                    assertThat(sessions.isSessionIdUrlRewritingEnabled()).isFalse();
                    assertThat(sessions.isDeleteInvalidSessions()).isFalse();

                    ShiroSessionManagerCookieProperties session = context.getBean(ShiroSessionManagerCookieProperties.class);
                    assertThat(session.getName()).isEqualTo("APPLICATION_SESSION");
                    assertThat(session.getMaxAge()).isEqualTo(600);
                    assertThat(session.getDomain()).isEqualTo("example.test");
                    assertThat(session.getPath()).isEqualTo("/app");
                    assertThat(session.isSecure()).isTrue();

                    ShiroRememberMeManagerCookieProperties remember = context.getBean(ShiroRememberMeManagerCookieProperties.class);
                    assertThat(remember.getName()).isEqualTo("APPLICATION_REMEMBER");
                    assertThat(remember.getMaxAge()).isEqualTo(1200);
                    assertThat(remember.getDomain()).isEqualTo("example.test");
                    assertThat(remember.getPath()).isEqualTo("/app");
                    assertThat(remember.isSecure()).isTrue();
                    assertThat(remember.getSecretKey()).isEqualTo("fixture-key");
                });
    }

    @Test
    void bindsKaptchaSettingsAndExposesTheTemplateState() {
        new ApplicationContextRunner().withUserConfiguration(KaptchaConfiguration.class)
                .withPropertyValues("shiro.kaptcha.enabled=true", "shiro.kaptcha.retry-times-when-access-denied=4",
                        "shiro.kaptcha.store-key=challenge", "shiro.kaptcha.date-store-key=issued",
                        "shiro.kaptcha.timeout=45000", "shiro.kaptcha.cache-key=login-captcha")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ShiroKaptchaProperties properties = context.getBean(ShiroKaptchaProperties.class);
                    assertThat(properties.getRetryTimesWhenAccessDenied()).isEqualTo(4);
                    assertThat(properties.getStoreKey()).isEqualTo("challenge");
                    assertThat(properties.getDateStoreKey()).isEqualTo("issued");
                    assertThat(properties.getTimeout()).isEqualTo(45_000L);
                    assertThat(properties.getCacheKey()).isEqualTo("login-captcha");
                    CaptchaEnabled captchaEnabled = context.getBean(CaptchaEnabled.class);
                    assertThat(captchaEnabled.exec(Collections.emptyList()).toString()).isEqualTo("true");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ShiroSessionManagerProperties.class, ShiroSessionManagerCookieProperties.class,
            ShiroRememberMeManagerCookieProperties.class})
    static class PropertiesConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ShiroKaptchaProperties.class)
    @Import(CaptchaEnabled.class)
    static class KaptchaConfiguration {
    }
}
