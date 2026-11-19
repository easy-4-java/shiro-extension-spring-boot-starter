package org.apache.shiro.spring.boot;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.StringUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiroAutoConfigurationImportsTest {

    @Test
    void bootDiscoversAllStarterConfigurationsListedInSpringFactories() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        var factories = PropertiesLoaderUtils.loadProperties(new ClassPathResource("META-INF/spring.factories", loader));
        String registered = factories.getProperty(EnableAutoConfiguration.class.getName());
        List<String> starterConfigurations = Arrays.asList(StringUtils.commaDelimitedListToStringArray(registered));
        assertFalse(starterConfigurations.isEmpty());

        List<String> discovered = ImportCandidates.load(AutoConfiguration.class, loader).getCandidates();
        List<String> missing = starterConfigurations.stream().filter(name -> !discovered.contains(name)).toList();
        assertTrue(missing.isEmpty(), () -> "Starter configurations missing from Boot auto-configuration imports: " + missing);
    }
}
