package org.apache.shiro.spring.boot;

import org.apache.shiro.authz.permission.RolePermissionResolver;
import org.apache.shiro.authz.permission.WildcardPermission;
import org.apache.shiro.biz.realm.AuthorizingRealmListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ShiroBizPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShiroBizAutoConfiguration.class));

    @Test
    void bindsAuthenticationPolicyAndSuppliesDefaultRolePermissions() {
        AuthorizingRealmListener listener = mock(AuthorizingRealmListener.class);
        contextRunner.withBean(AuthorizingRealmListener.class, () -> listener)
                .withPropertyValues("shiro.enabled=true", "shiro.authorization-caching-enabled=true",
                        "shiro.authentication-caching-enabled=true", "shiro.authorization-cache-name=roles",
                        "shiro.authentication-cache-name=logins", "shiro.credentials-retry-times-limit=4",
                        "shiro.credentials-retry-cache-name=attempts", "shiro.captcha-enabled=true",
                        "shiro.captcha-param-name=challenge", "shiro.retry-times-key-attribute=failedAttempts",
                        "shiro.retry-times-when-access-denied=3", "shiro.default-role-permissions.auditor=reports:read")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ShiroBizProperties properties = context.getBean(ShiroBizProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isCachingEnabled()).isTrue();
                    assertThat(properties.isAuthorizationCachingEnabled()).isTrue();
                    assertThat(properties.isAuthenticationCachingEnabled()).isTrue();
                    assertThat(properties.getAuthorizationCacheName()).isEqualTo("roles");
                    assertThat(properties.getAuthenticationCacheName()).isEqualTo("logins");
                    assertThat(properties.getCredentialsRetryTimesLimit()).isEqualTo(4);
                    assertThat(properties.getCredentialsRetryCacheName()).isEqualTo("attempts");
                    assertThat(properties.isCaptchaEnabled()).isTrue();
                    assertThat(properties.getCaptchaParamName()).isEqualTo("challenge");
                    assertThat(properties.getRetryTimesKeyAttribute()).isEqualTo("failedAttempts");
                    assertThat(properties.getRetryTimesWhenAccessDenied()).isEqualTo(3);
                    RolePermissionResolver resolver = context.getBean(RolePermissionResolver.class);
                    assertThat(resolver.resolvePermissionsInRole("auditor")).containsExactly(new WildcardPermission("reports:read"));
                    assertThat(context.getBean("realmListeners", java.util.List.class)).containsExactly(listener);
                    properties.setCachingEnabled(false);
                    assertThat(properties.isAuthorizationCachingEnabled()).isFalse();
                    assertThat(properties.isAuthenticationCachingEnabled()).isFalse();
                });
    }

    @Test
    void bindsSessionPolicyAndLoginDestinations() {
        contextRunner.withPropertyValues("shiro.session-creation-enabled=false", "shiro.session-storage-enabled=false",
                        "shiro.session-stateless=true", "shiro.session-maximum-kickout=2", "shiro.kickout-first=true",
                        "shiro.session-deque-cache-name=active-users", "shiro.unique-sessin=true",
                        "shiro.user-native-session-manager=true", "shiro.post-only-logout=true",
                        "shiro.login-url=/login", "shiro.success-url=/home", "shiro.failure-url=/failure",
                        "shiro.unauthorized-url=/denied", "shiro.redirect-url=/signed-out")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ShiroBizProperties properties = context.getBean(ShiroBizProperties.class);
                    assertThat(properties.isSessionCreationEnabled()).isFalse();
                    assertThat(properties.isSessionStorageEnabled()).isFalse();
                    assertThat(properties.isSessionStateless()).isTrue();
                    assertThat(properties.getSessionMaximumKickout()).isEqualTo(2);
                    assertThat(properties.isKickoutFirst()).isTrue();
                    assertThat(properties.getSessionDequeCacheName()).isEqualTo("active-users");
                    assertThat(properties.isUniqueSessin()).isTrue();
                    assertThat(properties.isUserNativeSessionManager()).isTrue();
                    assertThat(properties.isPostOnlyLogout()).isTrue();
                    assertThat(properties.getLoginUrl()).isEqualTo("/login");
                    assertThat(properties.getSuccessUrl()).isEqualTo("/home");
                    assertThat(properties.getFailureUrl()).isEqualTo("/failure");
                    assertThat(properties.getUnauthorizedUrl()).isEqualTo("/denied");
                    assertThat(properties.getRedirectUrl()).isEqualTo("/signed-out");
                });
    }
}
