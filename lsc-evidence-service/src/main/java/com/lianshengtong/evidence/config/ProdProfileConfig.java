package com.lianshengtong.evidence.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!standalone")
@ComponentScan(
    basePackages = {
        "com.lianshengtong.evidence.mapper",
        "com.lianshengtong.evidence.service.impl",
        "com.lianshengtong.evidence.service",
        "com.lianshengtong.evidence.schedule"
    },
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.evidence\\.service\\.MockEvidenceService"
        )
    }
)
public class ProdProfileConfig {
}
