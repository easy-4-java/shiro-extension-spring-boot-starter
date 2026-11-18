package org.apache.shiro.spring.boot;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.biz.authc.DefaultAuthenticationFailureHandler;
import org.apache.shiro.event.EventBus;
import org.apache.shiro.event.support.DefaultEventBus;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.spring.boot.cache.ShiroMemoryCacheConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class ShiroBizServletConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShiroBizAutoConfiguration.class, ShiroBizWebAutoConfiguration.class,
                    ShiroBizWebFilterConfiguration.class, ShiroMemoryCacheConfiguration.class))
            .withBean(EventBus.class, DefaultEventBus::new)
            .withBean(Realm.class, SimpleAccountRealm::new)
            .withPropertyValues("shiro.static-security-manager-enabled=false");

    @Test
    void starterRegistersServletAutoConfigurationsForBoot() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(ShiroBizAutoConfiguration.class.getName(), ShiroBizWebAutoConfiguration.class.getName(),
                        ShiroBizWebFilterConfiguration.class.getName());
    }

    @Test
    void configuredMethodsAreEnforcedOnlyOnTheirShiroPath() {
        contextRunner
                .withPropertyValues("shiro.enabled=true",
                        "shiro.http.header.access-control-allow-methods=GET, POST",
                        "shiro.filter-chain-definition-map.[/protected]=methods",
                        "shiro.filter-chain-definition-map.[/public]=anon")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DefaultAuthenticationFailureHandler.class);
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

    private void assertRequest(Filter filter, String method, String path, int status, boolean reachedServlet)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(status);
        if (reachedServlet) {
            assertThat(chain.getRequest()).isNotNull();
        } else {
            assertThat(chain.getRequest()).isNull();
        }
    }
}
