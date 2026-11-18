package org.apache.shiro.spring.boot;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.event.EventBus;
import org.apache.shiro.event.support.DefaultEventBus;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.SessionListener;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO;
import org.apache.shiro.session.mgt.eis.MemorySessionDAO;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.apache.shiro.spring.boot.cache.ShiroMemoryCacheConfiguration;
import org.apache.shiro.web.session.mgt.DefaultWebSessionContext;
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager;
import org.apache.shiro.web.session.mgt.ServletContainerSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ShiroBizServletConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShiroBizWebAutoConfiguration.class,
                    ShiroBizWebFilterConfiguration.class, ShiroMemoryCacheConfiguration.class))
            .withBean(EventBus.class, DefaultEventBus::new)
            .withBean(Realm.class, SimpleAccountRealm::new)
            .withPropertyValues("shiro.static-security-manager-enabled=false");

    @Test
    void configuredMethodsAreEnforcedOnlyOnTheirShiroPath() {
        contextRunner
                .withPropertyValues("shiro.enabled=true",
                        "shiro.http.header.access-control-allow-methods=GET, POST",
                        "shiro.filter-chain-definition-map.[/protected]=methods",
                        "shiro.filter-chain-definition-map.[/public]=anon")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FilterRegistrationBean<?> methods = context.getBean("methods", FilterRegistrationBean.class);
                    assertThat(methods.isEnabled()).isFalse();
                    Filter filter = context.getBean("filterShiroFilterRegistrationBean", FilterRegistrationBean.class)
                            .getFilter();

                    assertRequest(filter, "GET", "/protected", HttpServletResponse.SC_OK, true);
                    assertRequest(filter, "POST", "/protected", HttpServletResponse.SC_OK, true);
                    assertRequest(filter, "DELETE", "/protected", HttpServletResponse.SC_FORBIDDEN, false);
                    assertRequest(filter, "DELETE", "/public", HttpServletResponse.SC_OK, true);
                });
    }

    @Test
    void nativeSessionsUseConfiguredCacheTimeoutAndListener() {
        SessionListener listener = mock(SessionListener.class);
        contextRunner.withBean(SessionListener.class, () -> listener)
                .withPropertyValues("shiro.enabled=true", "shiro.userNativeSessionManager=true",
                        "shiro.cache.type=memory", "shiro.session-caching-enabled=true",
                        "shiro.active-sessions-cache-name=test-sessions", "shiro.session-timeout=55000",
                        "shiro.session-validation-interval=25000", "shiro.session-validation-scheduler-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SessionDAO dao = context.getBean(SessionDAO.class);
                    assertThat(dao).isInstanceOf(EnterpriseCacheSessionDAO.class);
                    assertThat(((EnterpriseCacheSessionDAO) dao).getActiveSessionsCacheName()).isEqualTo("test-sessions");
                    DefaultWebSessionManager manager = context.getBean(DefaultWebSessionManager.class);
                    assertThat(manager.getSessionDAO()).isSameAs(dao);
                    assertThat(manager.getSessionValidationInterval()).isEqualTo(25_000L);
                    assertThat(manager.isSessionValidationSchedulerEnabled()).isFalse();

                    DefaultWebSessionContext sessionContext = new DefaultWebSessionContext();
                    sessionContext.setServletRequest(new MockHttpServletRequest());
                    sessionContext.setServletResponse(new MockHttpServletResponse());
                    Session session = manager.start(sessionContext);

                    assertThat(dao.readSession(session.getId()).getTimeout()).isEqualTo(55_000L);
                    verify(listener).onStart(any(Session.class));
                    session.stop();
                });
    }

    @Test
    void containerSessionsRemainTheDefaultWithoutNativeSessionConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("sessionManager", SessionManager.class)).isInstanceOf(ServletContainerSessionManager.class);
            assertThat(context.getBean(SessionDAO.class)).isInstanceOf(MemorySessionDAO.class);
            assertThat(context).doesNotHaveBean("filterShiroFilterRegistrationBean");
        });
    }

    private void assertRequest(Filter filter, String method, String path, int status, boolean reachedServlet)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(chain.getRequest() != null).isEqualTo(reachedServlet);
    }
}
