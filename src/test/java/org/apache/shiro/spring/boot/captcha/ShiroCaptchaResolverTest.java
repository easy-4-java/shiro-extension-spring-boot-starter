package org.apache.shiro.spring.boot.captcha;

import com.google.code.kaptcha.spring.boot.ext.KaptchaResolver;
import com.google.code.kaptcha.spring.boot.ext.exception.CaptchaIncorrectException;
import com.google.code.kaptcha.spring.boot.ext.exception.CaptchaTimeoutException;
import com.google.code.kaptcha.util.Config;
import com.octo.captcha.service.image.ImageCaptchaService;
import ml.miron.captcha.util.CaptchaServletUtil;
import nl.captcha.Captcha;
import org.apache.shiro.biz.authc.exception.IncorrectCaptchaException;
import org.apache.shiro.biz.authc.exception.InvalidCaptchaException;
import org.apache.shiro.biz.authc.token.CaptchaAuthenticationToken;
import org.apache.shiro.biz.web.filter.authc.captcha.CaptchaResolver;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.MapCache;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Date;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShiroCaptchaResolverTest {

    @Test
    void jcaptchaValidatesAgainstCurrentHttpSession() {
        ImageCaptchaService service = mock(ImageCaptchaService.class);
        CaptchaAuthenticationToken token = mock(CaptchaAuthenticationToken.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(token.getCaptcha()).thenReturn("answer");
        when(service.validateResponseForID(request.getSession().getId(), "answer")).thenReturn(Boolean.TRUE);

        assertThat(new ShiroJCaptchaCaptchaResolver(service).validCaptcha(request, token)).isTrue();
        verify(service).validateResponseForID(request.getSession().getId(), "answer");
    }

    @Test
    void jcaptchaRejectsBlankAnswersBeforeCallingService() {
        ImageCaptchaService service = mock(ImageCaptchaService.class);
        CaptchaAuthenticationToken token = mock(CaptchaAuthenticationToken.class);
        when(token.getCaptcha()).thenReturn("");

        assertThatThrownBy(() -> new ShiroJCaptchaCaptchaResolver(service)
                .validCaptcha(new MockHttpServletRequest(), token))
                .isInstanceOf(IncorrectCaptchaException.class);
    }

    @Test
    void kaptchaCacheKeepsAnswerAndTimestampUnderDifferentKeys() {
        @SuppressWarnings("unchecked")
        Cache<String, Object> cache = mock(Cache.class);
        ShiroKaptchaCacheResolver resolver = new ShiroKaptchaCacheResolver(cache);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Date created = new Date();

        assertThat(resolver.getCaptchaDateStoreKey()).isNotEqualTo(resolver.getCaptchaStoreKey());
        ShiroKaptchaSessionResolver sessionResolver = new ShiroKaptchaSessionResolver();
        assertThat(sessionResolver.getCaptchaDateStoreKey()).isNotEqualTo(sessionResolver.getCaptchaStoreKey());
        resolver.setCaptcha(request, new MockHttpServletResponse(), "answer", created);
        verify(cache).put(resolver.getCaptchaStoreKey(), "answer");
        verify(cache).put(resolver.getCaptchaDateStoreKey(), created);
    }
    @Test
    void cacheResolverRejectsAnAnswerWithoutItsTimestamp() {
        Cache<String, Object> cache = new MapCache<>("captcha", new HashMap<>());
        ShiroKaptchaCacheResolver resolver = new ShiroKaptchaCacheResolver(cache);
        cache.put(resolver.getCaptchaStoreKey(), "answer");
        CaptchaAuthenticationToken token = mock(CaptchaAuthenticationToken.class);
        when(token.getCaptcha()).thenReturn("answer");

        assertThatThrownBy(() -> resolver.validCaptcha(new MockHttpServletRequest(), token))
                .isInstanceOf(InvalidCaptchaException.class);
    }

    @Test
    void sessionResolverRejectsAnAnswerWithoutItsTimestamp() {
        Subject subject = new Subject.Builder(new DefaultSecurityManager()).buildSubject();
        ShiroKaptchaSessionResolver resolver = new ShiroKaptchaSessionResolver();
        subject.getSession().setAttribute(resolver.getCaptchaStoreKey(), "answer");
        CaptchaAuthenticationToken token = mock(CaptchaAuthenticationToken.class);
        when(token.getCaptcha()).thenReturn("answer");

        subject.execute(() -> {
            assertThatThrownBy(() -> resolver.validCaptcha(new MockHttpServletRequest(), token))
                    .isInstanceOf(InvalidCaptchaException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void kaptchaResolversApplyConfiguredKeysAndTimeout(boolean useCache) {
        Subject subject = new Subject.Builder(new DefaultSecurityManager()).buildSubject();
        KaptchaResolver resolver = useCache
                ? new ShiroKaptchaCacheResolver(new MapCache<>("captcha", new HashMap<>()))
                : new ShiroKaptchaSessionResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        subject.execute(() -> {
            resolver.init("answerKey", "timestampKey", 60_000L);
            assertThatThrownBy(() -> resolver.validCaptcha(request, "answer"))
                    .isInstanceOf(CaptchaIncorrectException.class);
            resolver.setCaptcha(request, response, "Answer", null);
            assertThat(resolver.validCaptcha(request, "answer")).isTrue();
            assertThat(resolver.validCaptcha(request, "wrong")).isFalse();
            assertThatThrownBy(() -> resolver.validCaptcha(request, ""))
                    .isInstanceOf(CaptchaIncorrectException.class);

            resolver.setCaptcha(request, response, "Answer", new Date(0L));
            assertThatThrownBy(() -> resolver.validCaptcha(request, "answer"))
                    .isInstanceOf(CaptchaTimeoutException.class);
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void simpleAndSmartCaptchaValidateTheChallengeInTheCurrentSession(boolean smart) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CaptchaAuthenticationToken token = mock(CaptchaAuthenticationToken.class);
        CaptchaResolver resolver = smart
                ? new ShiroSmartCaptchaCaptchaResolver()
                : new ShiroSimpleCaptchaCaptchaResolver();
        when(token.getCaptcha()).thenReturn("");
        assertThatThrownBy(() -> resolver.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);
        when(token.getCaptcha()).thenReturn("answer");
        assertThatThrownBy(() -> resolver.validCaptcha(request, token))
                .isInstanceOf(IncorrectCaptchaException.class)
                .hasCauseInstanceOf(InvalidCaptchaException.class);

        if (smart) {
            request.getSession().setAttribute(CaptchaServletUtil.CAPTCHA_ATTRIBUTE, "ANSWER");
        } else {
            Captcha challenge = new Captcha.Builder(120, 50).addText(() -> "answer").build();
            request.getSession().setAttribute(Captcha.NAME, challenge);
        }
        assertThat(resolver.validCaptcha(request, token)).isTrue();
        when(token.getCaptcha()).thenReturn("wrong");
        assertThat(resolver.validCaptcha(request, token)).isFalse();
    }

    @Test
    void readsKaptchaServletKeysForCacheAndSessionValidation() {
        Config config = mock(Config.class);
        when(config.getSessionKey()).thenReturn("challenge");
        when(config.getSessionDate()).thenReturn("issued");
        Cache<String, Object> cache = new MapCache<>("captcha", new HashMap<>());
        ShiroKaptchaCacheResolver cached = new ShiroKaptchaCacheResolver(cache);
        ShiroKaptchaSessionResolver session = new ShiroKaptchaSessionResolver();
        cached.init(config);
        session.init(config);

        assertThat(cached.getCaptchaStoreKey()).isEqualTo("challenge");
        assertThat(cached.getCaptchaDateStoreKey()).isEqualTo("issued");
        assertThat(session.getCaptchaStoreKey()).isEqualTo("challenge");
        assertThat(session.getCaptchaDateStoreKey()).isEqualTo("issued");

        MockHttpServletRequest request = new MockHttpServletRequest();
        CaptchaAuthenticationToken token = mock(CaptchaAuthenticationToken.class);
        assertThatThrownBy(() -> cached.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);
        when(token.getCaptcha()).thenReturn("answer");
        assertThatThrownBy(() -> cached.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);

        Subject subject = new Subject.Builder(new DefaultSecurityManager()).buildSubject();
        subject.execute(() -> {
            assertThatThrownBy(() -> session.validCaptcha(request, token)).isInstanceOf(IncorrectCaptchaException.class);
            session.setCaptcha(request, new MockHttpServletResponse(), "Answer", new Date());
            assertThat(session.validCaptcha(request, token)).isTrue();
        });
        cached.setCaptcha(request, new MockHttpServletResponse(), "Answer", new Date());
        assertThat(cached.validCaptcha(request, token)).isTrue();
    }

}
