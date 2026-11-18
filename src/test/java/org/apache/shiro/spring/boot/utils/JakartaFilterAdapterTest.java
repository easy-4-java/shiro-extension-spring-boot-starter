package org.apache.shiro.spring.boot.utils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JakartaFilterAdapterTest {

    @Test
    void forwardsLegacyHttpWrappersAndAnUpdatedResponse() throws Exception {
        AtomicBoolean destroyed = new AtomicBoolean();
        javax.servlet.Filter legacy = new javax.servlet.Filter() {
            @Override
            public void init(javax.servlet.FilterConfig config) {
                assertThat(config.getFilterName()).isEqualTo("legacy");
                assertThat(config.getServletContext().getContextPath()).isEmpty();
            }

            @Override
            public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response,
                                 javax.servlet.FilterChain chain) throws IOException, javax.servlet.ServletException {
                javax.servlet.http.HttpServletRequest http = (javax.servlet.http.HttpServletRequest) request;
                assertThat(http.getMethod()).isEqualTo("GET");
                assertThat(http.getHeader("X-Request-Id")).isEqualTo("fixture");
                http.getSession().setAttribute("marker", "legacy");
                javax.servlet.http.HttpServletResponse outgoing = (javax.servlet.http.HttpServletResponse) response;
                outgoing.setHeader("X-Session", "created");
                chain.doFilter(new javax.servlet.http.HttpServletRequestWrapper(http) {
                    @Override
                    public String getRemoteUser() {
                        return "fixture-user";
                    }
                }, outgoing);
            }

            @Override
            public void destroy() {
                destroyed.set(true);
            }
        };
        JakartaFilterAdapter adapter = new JakartaFilterAdapter(legacy);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reports");
        request.addHeader("X-Request-Id", "fixture");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedChain = new AtomicBoolean();

        adapter.init(new MockFilterConfig("legacy"));
        adapter.doFilter(request, response, (next, res) -> {
            assertThat(((jakarta.servlet.http.HttpServletRequest) next).getRemoteUser()).isEqualTo("fixture-user");
            assertThat(res).isSameAs(response);
            reachedChain.set(true);
        });
        adapter.destroy();

        assertThat(reachedChain).isTrue();
        assertThat(destroyed).isTrue();
        assertThat(request.getSession().getAttribute("marker")).isEqualTo("legacy");
        assertThat(response.getHeader("X-Session")).isEqualTo("created");
    }

    @Test
    void forwardsCookiesInBothDirections() throws Exception {
        JakartaFilterAdapter adapter = new JakartaFilterAdapter((LegacyFilter) (request, response, chain) -> {
            javax.servlet.http.HttpServletRequest incoming = (javax.servlet.http.HttpServletRequest) request;
            assertThat(incoming.getCookies()).singleElement().satisfies(cookie -> {
                assertThat(cookie.getName()).isEqualTo("session");
                assertThat(cookie.getValue()).isEqualTo("fixture");
            });
            javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("remember", "fixture");
            cookie.setPath("/app");
            cookie.setDomain("example.com");
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setMaxAge(60);
            ((javax.servlet.http.HttpServletResponse) response).addCookie(cookie);
            chain.doFilter(request, response);
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        jakarta.servlet.http.Cookie incomingCookie = new jakarta.servlet.http.Cookie("session", "fixture");
        incomingCookie.setDomain("example.com");
        request.setCookies(incomingCookie);
        MockHttpServletResponse response = new MockHttpServletResponse();

        adapter.doFilter(request, response, (incoming, outgoing) -> {});

        assertThat(response.getCookie("remember")).satisfies(cookie -> {
            assertThat(cookie.getPath()).isEqualTo("/app");
            assertThat(cookie.getDomain()).isEqualTo("example.com");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSecure()).isTrue();
            assertThat(cookie.getMaxAge()).isEqualTo(60);
        });
    }

    @Test
    void propagatesIoAndServletExceptionsFromTheDelegateAndChain() {
        IOException io = new IOException("fixture IO");
        JakartaFilterAdapter failing = new JakartaFilterAdapter((LegacyFilter) (request, response, chain) -> {
            throw io;
        });
        assertThatThrownBy(() -> failing.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {})).isSameAs(io);

        jakarta.servlet.ServletException downstream = new jakarta.servlet.ServletException("fixture chain");
        JakartaFilterAdapter forwarding = new JakartaFilterAdapter((LegacyFilter)
                (request, response, chain) -> chain.doFilter(request, response));
        assertThatThrownBy(() -> forwarding.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> { throw downstream; })).isSameAs(downstream);
    }

    @Test
    void passesJakartaFiltersTheOriginalRequestAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reports");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaFilterAdapter adapter = new JakartaFilterAdapter((jakarta.servlet.Filter)
                (incoming, outgoing, chain) -> chain.doFilter(incoming, outgoing));
        AtomicBoolean reachedChain = new AtomicBoolean();

        adapter.doFilter(request, response, (incoming, outgoing) -> {
            assertThat(incoming).isSameAs(request);
            assertThat(outgoing).isSameAs(response);
            reachedChain.set(true);
        });

        assertThat(reachedChain).isTrue();
    }

    @Test
    void preservesJakartaFilterLifecycleAndPropagatesLegacyInitErrors() throws Exception {
        MockFilterConfig config = new MockFilterConfig("shiro");
        jakarta.servlet.Filter nativeFilter = mock(jakarta.servlet.Filter.class);
        JakartaFilterAdapter nativeAdapter = new JakartaFilterAdapter(nativeFilter);

        nativeAdapter.init(config);
        nativeAdapter.destroy();
        verify(nativeFilter).init(config);
        verify(nativeFilter).destroy();

        javax.servlet.Filter legacy = mock(javax.servlet.Filter.class);
        javax.servlet.ServletException failure = new javax.servlet.ServletException("fixture init");
        doThrow(failure).when(legacy).init(any());
        JakartaFilterAdapter legacyAdapter = new JakartaFilterAdapter(legacy);
        assertThatThrownBy(() -> legacyAdapter.init(config))
                .isInstanceOf(jakarta.servlet.ServletException.class).hasCause(failure);
        assertThatThrownBy(() -> new JakartaFilterAdapter(new Object())).isInstanceOf(IllegalArgumentException.class);
    }

    @FunctionalInterface
    private interface LegacyFilter extends javax.servlet.Filter {
        @Override
        default void init(javax.servlet.FilterConfig config) {
        }

        @Override
        default void destroy() {
        }
    }
}
