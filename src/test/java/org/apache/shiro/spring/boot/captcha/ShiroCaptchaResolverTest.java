package org.apache.shiro.spring.boot.captcha;

import com.octo.captcha.service.image.ImageCaptchaService;
import org.apache.shiro.biz.authc.exception.IncorrectCaptchaException;
import org.apache.shiro.biz.authc.token.CaptchaAuthenticationToken;
import org.apache.shiro.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Date;

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
}
