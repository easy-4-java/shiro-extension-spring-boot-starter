package org.apache.shiro.spring.boot.template.method;


import java.util.List;

import org.springframework.beans.factory.annotation.Value;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;

/**
 * 提供Freemarker模板判断是否启用验证码功能
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
public class CaptchaEnabled implements TemplateMethodModelEx {

	@Value("${shiro.kaptcha.enabled:false}")
	private boolean enabled;
	
	@SuppressWarnings("rawtypes")
	public Object exec(List arguments) throws TemplateModelException {
		return new SimpleScalar(Boolean.toString(enabled));
	}

}
