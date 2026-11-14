package com.qst.smartbuildings.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartBuildingsOpenAPI() {
        log.info("swagger已集成");
        return new OpenAPI()
                .info(new Info()
                        .title("智慧楼宇管理平台 API")
                        .description("智慧楼宇管理平台后端接口文档")
                        .version("v1.0.0")
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
