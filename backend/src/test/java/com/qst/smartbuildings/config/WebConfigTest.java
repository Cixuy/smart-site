package com.qst.smartbuildings.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.context.support.StaticApplicationContext;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebConfigTest {

    @TempDir
    Path root;

    @Test
    void registersPublicImageResourceMappings() {
        ModelStorageProperties modelStorageProperties = mock(ModelStorageProperties.class);
        when(modelStorageProperties.resourceLocation())
                .thenReturn(root.resolve("models").toUri().toString());

        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "modelStorageProperties", modelStorageProperties);
        ReflectionTestUtils.setField(config, "aiImageDir", root.resolve("ai_image").toString());

        ResourceHandlerRegistry registry = new ResourceHandlerRegistry(
                new StaticApplicationContext(), new MockServletContext());
        config.addResourceHandlers(registry);

        assertTrue(registry.hasMappingForPattern("/images/**"));
        assertTrue(registry.hasMappingForPattern("/certificate/**"));
        assertTrue(registry.hasMappingForPattern("/ai_image/**"));
        assertTrue(registry.hasMappingForPattern("/files/**"));
        assertTrue(registry.hasMappingForPattern("/device-models/**"));
    }
}
