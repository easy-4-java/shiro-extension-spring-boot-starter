package org.apache.shiro.spring.boot;

import java.util.List;

import jakarta.servlet.Filter;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.UnauthenticatedException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.biz.web.filter.HttpServletSessionDequeFilter;
import org.apache.shiro.biz.web.filter.authc.AuthenticatingFailureRequestCounter;
import org.apache.shiro.biz.web.filter.authc.AuthenticatingFailureSessionCounter;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.session.mgt.DefaultSessionManager;
import org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.apache.shiro.web.subject.WebSubject;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShiroConfiguredWebApplicationTest {

    @Test
    void registersConfiguredFiltersAndEnforcesThePathChain() {
        new WebApplicationContextRunner()
                .withUserConfiguration(ShiroStarterBootstrapTest.TestApplication.class)
                .withBean(AccountService.class)
                .withBean(FilterChainDefinitionConfigurer.class, () -> new FilterChainDefinitionConfigurer() {
                    @Override
                    public void configurePathDefinition(DefaultShiroFilterChainDefinition chain) {
                        chain.addPathDefinition("/public/**", "anon");
                    }
                })
                .withPropertyValues(
                        "shiro.enabled=true",
                        "shiro.cache.type=memory",
                        "shiro.login-url=/sign-in",
                        "shiro.success-url=/account",
                        "shiro.unauthorized-url=/denied",
                        "shiro.redirect-url=/signed-out",
                        "shiro.post-only-logout=true",
                        "shiro.http.header.access-control-allow-methods=GET, POST",
                        "shiro.filter-chain-definition-map.[/private/**]=authc",
                        "shiro.session-stateless=true",
                        "shiro.annotations.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuthenticatingFailureRequestCounter.class);
                    assertThat(context.getBean(ShiroFilterChainDefinition.class).getFilterChainMap())
                            .containsEntry("/private/**", "authc")
                            .containsEntry("/public/**", "anon");
                    for (String name : List.of("logout", "escapeHtml4", "headers", "methods", "referrers", "sessionExpired")) {
                        assertThat(context.getBean(name, FilterRegistrationBean.class).isEnabled()).isFalse();
                    }
                    Filter filter = context.getBean("filterShiroFilterRegistrationBean", FilterRegistrationBean.class).getFilter();
                    assertThat(request(filter, "/public/status").getStatus()).isEqualTo(200);
                    assertThat(request(filter, "/private/page").getRedirectedUrl()).isEqualTo("/sign-in");

                    AccountService accounts = context.getBean(AccountService.class);
                    var subject = new WebSubject.Builder(context.getBean(SecurityManager.class),
                            new MockHttpServletRequest(), new MockHttpServletResponse()).buildWebSubject();
                    assertThatThrownBy(() -> subject.execute(() -> accounts.readAccount()))
                            .hasCauseInstanceOf(UnauthenticatedException.class);
                    context.getBean(SimpleAccountRealm.class).addAccount("account-test", "fixture-password");
                    subject.login(new UsernamePasswordToken("account-test", "fixture-password"));
                    try {
                        assertThat(subject.execute(() -> accounts.readAccount())).isEqualTo("account");
                    } finally {
                        subject.logout();
                    }
                });
    }

    @Test
    void configuresTheNativeSessionManagerAndAnnotationProxy() {
        new WebApplicationContextRunner()
                .withUserConfiguration(ShiroStarterBootstrapTest.TestApplication.class)
                .withPropertyValues(
                        "shiro.enabled=true",
                        "shiro.cache.type=memory",
                        "shiro.userNativeSessionManager=true",
                        "shiro.session-caching-enabled=true",
                        "shiro.active-sessions-cache-name=active-by-id",
                        "shiro.kickout-first=true",
                        "shiro.session-maximum-kickout=4",
                        "shiro.session-deque-cache-name=account-sessions",
                        "shiro.session-timeout=47000",
                        "shiro.session-validation-interval=32000",
                        "shiro.session-validation-scheduler-enabled=false",
                        "shiro.annotations.enabled=true",
                        "shiro.annotations.proxy-target-class=false",
                        "shiro.annotations.expose-proxy=false",
                        "shiro.annotations.frozen=true",
                        "shiro.annotations.opaque=true",
                        "shiro.annotations.use-prefix=true",
                        "shiro.annotations.advisor-bean-name-prefix=security.")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DefaultSessionManager manager = context.getBean(DefaultSessionManager.class);
                    assertThat(manager.getGlobalSessionTimeout()).isEqualTo(47000L);
                    assertThat(manager.getSessionValidationInterval()).isEqualTo(32000L);
                    assertThat(manager.isSessionValidationSchedulerEnabled()).isFalse();
                    assertThat(manager.getSessionDAO()).isInstanceOfSatisfying(EnterpriseCacheSessionDAO.class, dao -> {
                        assertThat(dao.getActiveSessionsCacheName()).isEqualTo("active-by-id");
                        assertThat(dao.getCacheManager()).isSameAs(context.getBean(CacheManager.class));
                    });
                    assertThat(context).hasSingleBean(AuthenticatingFailureSessionCounter.class);
                    var status = context.getBean("sessionStatus", FilterRegistrationBean.class);
                    assertThat(status.isEnabled()).isFalse();
                    var registration = context.getBean("sessionDeque", FilterRegistrationBean.class);
                    assertThat(registration.isEnabled()).isFalse();
                    assertThat(registration.getFilter()).isInstanceOfSatisfying(HttpServletSessionDequeFilter.class, deque -> {
                        assertThat(deque.getSessionManager()).isSameAs(manager);
                        assertThat(deque.getSessionDequeCacheName()).isEqualTo("account-sessions");
                        assertThat(deque.isKickoutFirst()).isTrue();
                        assertThat(deque.getSessionMaximumKickout()).isEqualTo(4);
                    });
                    DefaultAdvisorAutoProxyCreator proxy = context.getBean(DefaultAdvisorAutoProxyCreator.class);
                    assertThat(proxy.isProxyTargetClass()).isFalse();
                    assertThat(proxy.isExposeProxy()).isFalse();
                    assertThat(proxy.isFrozen()).isTrue();
                    assertThat(proxy.isOpaque()).isTrue();
                    assertThat(proxy.isUsePrefix()).isTrue();
                    assertThat(proxy.getAdvisorBeanNamePrefix()).isEqualTo("security.");
                });
    }

    static class AccountService {

        @RequiresAuthentication
        public String readAccount() {
            return "account";
        }
    }

    private static MockHttpServletResponse request(Filter filter, String path) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
