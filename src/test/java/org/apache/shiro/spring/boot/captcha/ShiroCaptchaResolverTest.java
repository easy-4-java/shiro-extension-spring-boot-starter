package org.apache.shiro.spring.boot.captcha;

import com.octo.captcha.service.image.ImageCaptchaService;
import com.octo.captcha.spring.boot.servlet.image.SimpleImageCaptchaServlet;

import java.util.Date;
import java.util.HashMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.shiro.biz.authc.exception.IncorrectCaptchaException;
import org.apache.shiro.biz.authc.exception.InvalidCaptchaException;
import org.apache.shiro.biz.authc.token.CaptchaAuthenticationToken;
import org.apache.shiro.cache.MapCache;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShiroCaptchaResolverTest {

    private final CaptchaAuthenticationToken token = mock(CaptchaAuthenticationToken.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void nullResolverDoesNotAcceptCaptchaForJakartaRequests() {
        when(token.getCaptcha()).thenReturn("provided-captcha");

        assertThat(new NullCaptchaResolver().validCaptcha(request, token)).isFalse();
    }

    @Test
    void cacheResolverKeepsDefaultAnswerAndTimestampSeparate() {
        MapCache<String, Object> cache = new MapCache<>("captcha", new HashMap<>());
        ShiroKaptchaCacheResolver resolver = new ShiroKaptchaCacheResolver(cache);
        Date created = new Date();

        resolver.setCaptcha("Answer", created);

        assertThat(cache.get(resolver.getCaptchaStoreKey())).isEqualTo("Answer");
        assertThat(cache.get(resolver.getCaptchaDateStoreKey())).isEqualTo(created);
        when(token.getCaptcha()).thenReturn("answer");
        assertThat(resolver.validCaptcha(request, token)).isTrue();
        cache.remove(resolver.getCaptchaDateStoreKey());
        assertThatThrownBy(() -> resolver.validCaptcha(request, token)).isInstanceOf(InvalidCaptchaException.class);
    }

    @Test
    void sessionResolverKeepsDefaultAnswerAndTimestampSeparate() {
        ShiroKaptchaSessionResolver resolver = new ShiroKaptchaSessionResolver();
        Subject subject = new Subject.Builder(new DefaultSecurityManager()).buildSubject();
        when(token.getCaptcha()).thenReturn("answer");

        subject.execute(() -> {
            resolver.setCaptcha("Answer", null);
            assertThat(subject.getSession().getAttribute(resolver.getCaptchaStoreKey())).isEqualTo("Answer");
            assertThat(subject.getSession().getAttribute(resolver.getCaptchaDateStoreKey())).isInstanceOf(Date.class);
            assertThat(resolver.validCaptcha(request, token)).isTrue();
            subject.getSession().removeAttribute(resolver.getCaptchaDateStoreKey());
            assertThatThrownBy(() -> resolver.validCaptcha(request, token)).isInstanceOf(InvalidCaptchaException.class);
        });
    }

    @Test
    void configuredKeysAndTimeoutApplyToCachedAndSessionChallenges() {
        ShiroKaptchaCacheResolver cached = new ShiroKaptchaCacheResolver(new MapCache<>("captcha", new HashMap<>()));
        ShiroKaptchaSessionResolver session = new ShiroKaptchaSessionResolver();
        cached.init("challenge", "issued", 60000);
        session.init("challenge", "issued", 60000);

        assertThat(cached.getCaptchaStoreKey()).isEqualTo("challenge");
        assertThat(cached.getCaptchaDateStoreKey()).isEqualTo("issued");
        assertThat(session.getCaptchaStoreKey()).isEqualTo("challenge");
        assertThat(session.getCaptchaDateStoreKey()).isEqualTo("issued");
        when(token.getCaptcha()).thenReturn("");
        assertThatThrownBy(() -> cached.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);
        assertThatThrownBy(() -> session.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);

        when(token.getCaptcha()).thenReturn("answer");
        Subject subject = new Subject.Builder(new DefaultSecurityManager()).buildSubject();
        subject.execute(() -> {
            assertThatThrownBy(() -> cached.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);
            assertThatThrownBy(() -> session.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);
            cached.setCaptcha("answer", new Date(0));
            session.setCaptcha("answer", new Date(0));
            assertThatThrownBy(() -> cached.validCaptcha(request, token)).isInstanceOf(InvalidCaptchaException.class);
            assertThatThrownBy(() -> session.validCaptcha(request, token)).isInstanceOf(InvalidCaptchaException.class);
        });
    }

    @Test
    void jcaptchaUsesTheStarterServiceWithTheCurrentSessionId() {
        ImageCaptchaService original = SimpleImageCaptchaServlet.service;
        ImageCaptchaService service = mock(ImageCaptchaService.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);
        when(session.getId()).thenReturn("current-session");
        SimpleImageCaptchaServlet.service = service;

        try {
            ShiroJCaptchaCaptchaResolver resolver = new ShiroJCaptchaCaptchaResolver();
            when(token.getCaptcha()).thenReturn("");
            assertThatThrownBy(() -> resolver.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);
            when(token.getCaptcha()).thenReturn("answer");
            when(service.validateResponseForID("current-session", "answer")).thenReturn(Boolean.TRUE);
            assertThat(resolver.validCaptcha(request, token)).isTrue();
            when(service.validateResponseForID("current-session", "answer")).thenReturn(Boolean.FALSE);
            assertThat(resolver.validCaptcha(request, token)).isFalse();
        } finally {
            SimpleImageCaptchaServlet.service = original;
        }
    }

    @Test
    void jcaptchaReportsUnavailableAndFailingServices() {
        ImageCaptchaService original = SimpleImageCaptchaServlet.service;
        when(token.getCaptcha()).thenReturn("answer");
        ShiroJCaptchaCaptchaResolver resolver = new ShiroJCaptchaCaptchaResolver();

        try {
            SimpleImageCaptchaServlet.service = null;
            assertThatThrownBy(() -> resolver.validCaptcha(request, token))
                    .isInstanceOf(IncorrectCaptchaException.class).hasMessage("JCaptcha service is unavailable");

            ImageCaptchaService brokenService = mock(ImageCaptchaService.class);
            SimpleImageCaptchaServlet.service = brokenService;
            HttpSession session = mock(HttpSession.class);
            when(request.getSession()).thenReturn(session);
            when(session.getId()).thenReturn("current-session");
            when(brokenService.validateResponseForID("current-session", "answer"))
                    .thenThrow(new IllegalStateException("fixture service failure"));
            assertThatThrownBy(() -> resolver.validCaptcha(request, token))
                    .isInstanceOf(IncorrectCaptchaException.class).hasCauseInstanceOf(IllegalStateException.class);
        } finally {
            SimpleImageCaptchaServlet.service = original;
        }
    }

    @Test
    void smartCaptchaValidatesTheCurrentSessionChallenge() {
        ShiroSmartCaptchaCaptchaResolver resolver = new ShiroSmartCaptchaCaptchaResolver();
        HttpSession session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);
        when(token.getCaptcha()).thenReturn("answer");
        assertThatThrownBy(() -> resolver.validCaptcha(request, token)).isInstanceOf(InvalidCaptchaException.class);

        when(session.getAttribute("captcha")).thenReturn("ANSWER");
        assertThat(resolver.validCaptcha(request, token)).isTrue();
        when(token.getCaptcha()).thenReturn("wrong");
        assertThat(resolver.validCaptcha(request, token)).isFalse();
        when(token.getCaptcha()).thenReturn("");
        assertThatThrownBy(() -> resolver.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);
    }
}
