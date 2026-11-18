package org.apache.shiro.spring.boot;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.event.EventBus;
import org.apache.shiro.event.support.DefaultEventBus;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ShiroBizServletConfigurationTest {

    @Test
    void configuredMethodsAreEnforcedOnlyOnTheirShiroPath() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ShiroBizWebAutoConfiguration.class,
                        ShiroBizWebFilterConfiguration.class))
                .withBean(EventBus.class, DefaultEventBus::new)
                .withBean(Realm.class, SimpleAccountRealm::new)
                .withPropertyValues("shiro.enabled=true", "shiro.static-security-manager-enabled=false",
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
