package com.lianshengtong.common.tracing;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Configuration
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    @Value("${lsc.tracing.enabled:false}")
    private boolean tracingEnabled;

    @Value("${lsc.tracing.service-name:${spring.application.name:unknown}}")
    private String serviceName;

    @PostConstruct
    public void init() {
        if (tracingEnabled) {
            log.info("[TracingConfig] SkyWalking tracing enabled for service: {}", serviceName);
            log.info("[TracingConfig] Ensure SkyWalking agent is loaded via -javaagent JVM argument");
        }
    }


    public TracingConfig() {}

    public TracingConfig(boolean tracingEnabled, String serviceName) {
        this.tracingEnabled = tracingEnabled;
        this.serviceName = serviceName;
    }

    public boolean getTracingEnabled() { return tracingEnabled; }
    public void setTracingEnabled(boolean tracingEnabled) { this.tracingEnabled = tracingEnabled; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
}
