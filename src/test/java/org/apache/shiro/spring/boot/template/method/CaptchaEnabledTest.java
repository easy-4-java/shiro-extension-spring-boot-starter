package org.apache.shiro.spring.boot.template.method;

import java.util.List;
import java.util.Map;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateModelException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptchaEnabledTest {

    @Test
    void keepsTheLegacyEnablePropertyAndItsDefault() throws TemplateModelException {
        assertEquals("false", render(Map.of()));
        assertEquals("true", render(Map.of("shiro.kaptcha.enabled", "true")));
    }

    private static String render(Map<String, Object> properties) throws TemplateModelException {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("captcha", properties));
            context.register(CaptchaEnabled.class);
            context.refresh();
            var result = (SimpleScalar) context.getBean(CaptchaEnabled.class).exec(List.of());
            return result.getAsString();
        }
    }
}
