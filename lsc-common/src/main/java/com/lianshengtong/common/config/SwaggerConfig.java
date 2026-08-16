package com.lianshengtong.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


@Configuration
@Profile("!prod")
public class SwaggerConfig {

    @Value("${knife4j.enable:true}")
    private boolean knife4jEnable;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LSC System API")
                        .version("6.2.0-AI")
                        .description("联盛通权益服务平台 (Lian Sheng Tong Benefit Platform) — 基于 Spring Boot 3 + SpringDoc OpenAPI 3 的 RESTful API 文档")
                        .contact(new Contact()
                                .name("LSC Dev Team")
                                .email("dev@lianshengtong.com")));
    }


    public SwaggerConfig() {}

    public SwaggerConfig(boolean knife4jEnable) {
        this.knife4jEnable = knife4jEnable;
    }

    public boolean getKnife4jEnable() { return knife4jEnable; }
    public void setKnife4jEnable(boolean knife4jEnable) { this.knife4jEnable = knife4jEnable; }
}