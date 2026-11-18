package org.apache.shiro.spring.boot.utils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Adapts a legacy Servlet filter for registration in a Jakarta servlet container.
 */
public class JakartaFilterAdapter implements Filter {

    private static final String LEGACY = "javax.servlet";
    private static final String JAKARTA = "jakarta.servlet";

    private final Object delegate;

    public JakartaFilterAdapter(Object delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (!(delegate instanceof Filter) && !(delegate instanceof javax.servlet.Filter)) {
            throw new IllegalArgumentException("Expected a Servlet filter");
        }
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        if (delegate instanceof Filter filter) {
            filter.init(config);
            return;
        }
        try {
            ((javax.servlet.Filter) delegate).init(
                    bridge(config, javax.servlet.FilterConfig.class, jakarta.servlet.FilterConfig.class));
        } catch (javax.servlet.ServletException e) {
            throw toJakartaException(e);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (delegate instanceof Filter filter) {
            filter.doFilter(request, response, chain);
            return;
        }

        try {
            ((javax.servlet.Filter) delegate).doFilter(toLegacyRequest(request), toLegacyResponse(response),
                    (nextRequest, nextResponse) -> {
                        try {
                            chain.doFilter(toJakartaRequest(nextRequest), toJakartaResponse(nextResponse));
                        } catch (ServletException e) {
                            throw new javax.servlet.ServletException(e.getMessage(), e);
                        }
                    });
        } catch (javax.servlet.ServletException e) {
            throw toJakartaException(e);
        }
    }

    @Override
    public void destroy() {
        if (delegate instanceof Filter filter) {
            filter.destroy();
        } else {
            ((javax.servlet.Filter) delegate).destroy();
        }
    }

    /**
     * Returns the underlying Servlet filter.
     */
    public Object getDelegate() {
        return delegate;
    }

    private static javax.servlet.ServletRequest toLegacyRequest(ServletRequest request) {
        if (request instanceof jakarta.servlet.http.HttpServletRequest) {
            return bridge(request, javax.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletRequest.class);
        }
        return bridge(request, javax.servlet.ServletRequest.class, ServletRequest.class);
    }

    private static javax.servlet.ServletResponse toLegacyResponse(ServletResponse response) {
        if (response instanceof jakarta.servlet.http.HttpServletResponse) {
            return bridge(response, javax.servlet.http.HttpServletResponse.class, jakarta.servlet.http.HttpServletResponse.class);
        }
        return bridge(response, javax.servlet.ServletResponse.class, ServletResponse.class);
    }

    private static ServletRequest toJakartaRequest(javax.servlet.ServletRequest request) {
        if (request instanceof javax.servlet.http.HttpServletRequest) {
            return bridge(request, jakarta.servlet.http.HttpServletRequest.class, javax.servlet.http.HttpServletRequest.class);
        }
        return bridge(request, ServletRequest.class, javax.servlet.ServletRequest.class);
    }

    private static ServletResponse toJakartaResponse(javax.servlet.ServletResponse response) {
        if (response instanceof javax.servlet.http.HttpServletResponse) {
            return bridge(response, jakarta.servlet.http.HttpServletResponse.class, javax.servlet.http.HttpServletResponse.class);
        }
        return bridge(response, ServletResponse.class, javax.servlet.ServletResponse.class);
    }

    private static ServletException toJakartaException(javax.servlet.ServletException e) {
        if (e.getCause() instanceof ServletException original) {
            return original;
        }
        return new ServletException(e.getMessage(), e);
    }

    private static <T> T bridge(Object value, Class<T> exposedType, Class<?> targetType) {
        if (Objects.isNull(value) || exposedType.isInstance(value)) {
            return exposedType.cast(value);
        }
        if (exposedType.isArray()) {
            int length = Array.getLength(value);
            Class<?> component = exposedType.getComponentType();
            Object converted = Array.newInstance(component, length);
            for (int i = 0; i < length; i++) {
                Array.set(converted, i, bridge(Array.get(value, i), component, targetType.getComponentType()));
            }
            return exposedType.cast(converted);
        }
        if (value instanceof jakarta.servlet.http.Cookie cookie && exposedType == javax.servlet.http.Cookie.class) {
            return exposedType.cast(toLegacyCookie(cookie));
        }
        if (value instanceof javax.servlet.http.Cookie cookie && exposedType == jakarta.servlet.http.Cookie.class) {
            return exposedType.cast(toJakartaCookie(cookie));
        }
        // Unwrap our own proxy on the way back. User-supplied wrappers keep their overrides.
        if (Proxy.isProxyClass(value.getClass())
                && Proxy.getInvocationHandler(value) instanceof ServletBridge bridge
                && exposedType.isInstance(bridge.target)) {
            return exposedType.cast(bridge.target);
        }
        return exposedType.cast(Proxy.newProxyInstance(exposedType.getClassLoader(), new Class<?>[]{exposedType},
                new ServletBridge(value, exposedType, targetType)));
    }

    private static javax.servlet.http.Cookie toLegacyCookie(jakarta.servlet.http.Cookie source) {
        javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie(source.getName(), source.getValue());
        if (Objects.nonNull(source.getDomain())) {
            cookie.setDomain(source.getDomain());
        }
        cookie.setPath(source.getPath());
        cookie.setMaxAge(source.getMaxAge());
        cookie.setSecure(source.getSecure());
        cookie.setHttpOnly(source.isHttpOnly());
        return cookie;
    }

    private static jakarta.servlet.http.Cookie toJakartaCookie(javax.servlet.http.Cookie source) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(source.getName(), source.getValue());
        if (Objects.nonNull(source.getDomain())) {
            cookie.setDomain(source.getDomain());
        }
        cookie.setPath(source.getPath());
        cookie.setMaxAge(source.getMaxAge());
        cookie.setSecure(source.getSecure());
        cookie.setHttpOnly(source.isHttpOnly());
        return cookie;
    }

    private static Class<?> counterpart(Class<?> type, String from, String to) throws ClassNotFoundException {
        if (type.isArray()) {
            Class<?> component = type.getComponentType();
            Class<?> mapped = counterpart(component, from, to);
            return component == mapped ? type : mapped.arrayType();
        }
        String name = type.getName();
        if (name.startsWith(from + ".")) {
            return Class.forName(to + name.substring(from.length()), false, type.getClassLoader());
        }
        return type;
    }

    private record ServletBridge(Object target, Class<?> exposedType, Class<?> targetType) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(target, arguments);
            }
            String from = exposedType.getName().startsWith(LEGACY + ".") ? LEGACY : JAKARTA;
            String to = from.equals(LEGACY) ? JAKARTA : LEGACY;
            Class<?>[] exposedParameters = method.getParameterTypes();
            Class<?>[] targetParameters = new Class<?>[exposedParameters.length];
            Object[] targetArguments = new Object[exposedParameters.length];

            for (int i = 0; i < exposedParameters.length; i++) {
                Class<?> parameter = exposedParameters[i];
                Class<?> mapped = counterpart(parameter, from, to);
                targetParameters[i] = mapped;
                targetArguments[i] = mapped == parameter ? arguments[i] : bridge(arguments[i], mapped, parameter);
            }

            try {
                Object result = targetType.getMethod(method.getName(), targetParameters).invoke(target, targetArguments);
                Class<?> resultType = method.getReturnType();
                Class<?> mapped = counterpart(resultType, from, to);
                return mapped == resultType ? result : bridge(result, resultType, mapped);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof ServletException servlet) {
                    throw new javax.servlet.ServletException(servlet.getMessage(), servlet);
                }
                if (e.getCause() instanceof javax.servlet.ServletException servlet) {
                    throw toJakartaException(servlet);
                }
                throw e.getCause();
            }
        }
    }
}
