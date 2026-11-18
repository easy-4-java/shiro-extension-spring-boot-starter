package org.apache.shiro.spring.boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class ShiroBizPropertiesBindingTest {

    @Test
    void bindsTheAuthenticationSettingsAndEnablesTheRequestedCaches() {
        var properties = bind(Map.ofEntries(
                Map.entry("authorization-caching-enabled", true),
                Map.entry("authentication-caching-enabled", true),
                Map.entry("authorization-cache-name", "permissions"),
                Map.entry("authentication-cache-name", "credentials"),
                Map.entry("credentials-retry-times-limit", 7),
                Map.entry("credentials-retry-cache-name", "retry-by-account"),
                Map.entry("retry-times-key-attribute", "retryCount"),
                Map.entry("retry-times-when-access-denied", 2),
                Map.entry("captcha-enabled", true),
                Map.entry("captcha-param-name", "imageCode"),
                Map.entry("default-role-permissions.reader", "orders:read")));
        assertThat(properties.isCachingEnabled()).isTrue();
        assertThat(properties.isAuthenticationCachingEnabled()).isTrue();
        assertThat(properties.isAuthorizationCachingEnabled()).isTrue();
        assertThat(properties.getAuthenticationCacheName()).isEqualTo("credentials");
        assertThat(properties.getAuthorizationCacheName()).isEqualTo("permissions");
        assertThat(properties.getCredentialsRetryTimesLimit()).isEqualTo(7);
        assertThat(properties.getCredentialsRetryCacheName()).isEqualTo("retry-by-account");
        assertThat(properties.getRetryTimesKeyAttribute()).isEqualTo("retryCount");
        assertThat(properties.getRetryTimesWhenAccessDenied()).isEqualTo(2);
        assertThat(properties.isCaptchaEnabled()).isTrue();
        assertThat(properties.getCaptchaParamName()).isEqualTo("imageCode");
        assertThat(properties.getDefaultRolePermissions()).containsEntry("reader", "orders:read");
    }

    @Test
    void keepsTheConfiguredSessionAndFailurePolicy() {
        var properties = bind(Map.ofEntries(
                Map.entry("enabled", true),
                Map.entry("session-creation-enabled", false),
                Map.entry("session-storage-enabled", false),
                Map.entry("session-stateless", true),
                Map.entry("session-deque-cache-name", "session-queue"),
                Map.entry("session-maximum-kickout", 4),
                Map.entry("kickout-first", true),
                Map.entry("static-security-manager-enabled", true),
                Map.entry("unique-sessin", true),
                Map.entry("failure-url", "/sign-in/failed")));
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isSessionCreationEnabled()).isFalse();
        assertThat(properties.isSessionStorageEnabled()).isFalse();
        assertThat(properties.isSessionStateless()).isTrue();
        assertThat(properties.getSessionDequeCacheName()).isEqualTo("session-queue");
        assertThat(properties.getSessionMaximumKickout()).isEqualTo(4);
        assertThat(properties.isKickoutFirst()).isTrue();
        assertThat(properties.isStaticSecurityManagerEnabled()).isTrue();
        assertThat(properties.isUniqueSessin()).isTrue();
        assertThat(properties.getFailureUrl()).isEqualTo("/sign-in/failed");
    }

    private static ShiroBizProperties bind(Map<String, Object> values) {
        var source = new MapConfigurationPropertySource();
        values.forEach((key, value) -> source.put("shiro." + key, value));
        return new Binder(source).bind("shiro", ShiroBizProperties.class).get();
    }
}
