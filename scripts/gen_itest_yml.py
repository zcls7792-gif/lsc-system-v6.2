#!/usr/bin/env python3
import os

EXCLUDES_SERVLET = """  autoconfigure:
    exclude:
      - org.redisson.spring.starter.RedissonAutoConfigurationV2
      - com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure
      - io.seata.spring.boot.autoconfigure.SeataAutoConfiguration
      - io.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration
      - io.seata.spring.boot.autoconfigure.SeataHttpAutoConfiguration
      - io.seata.spring.boot.autoconfigure.SeataSagaAutoConfiguration
      - com.alibaba.cloud.nacos.NacosConfigAutoConfiguration
      - com.alibaba.cloud.nacos.endpoint.NacosConfigEndpointAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration
      - com.alibaba.cloud.nacos.endpoint.NacosDiscoveryEndpointAutoConfiguration
      - com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryHeartBeatConfiguration
      - com.alibaba.cloud.nacos.discovery.reactive.NacosReactiveDiscoveryClientConfiguration
      - com.alibaba.cloud.nacos.NacosServiceAutoConfiguration
      - com.alibaba.cloud.nacos.loadbalancer.LoadBalancerNacosAutoConfiguration
      - org.springframework.cloud.autoconfigure.RefreshAutoConfiguration
      - org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration
      - org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration
"""

def servlet_template(app, db):
    return f"""server:
  port: 0
  shutdown: graceful

spring:
  application:
    name: {app}
  main:
    web-application-type: servlet
    banner-mode: off
    allow-bean-definition-overriding: true
    allow-circular-references: true
  cloud:
    bootstrap:
      enabled: false
    nacos:
      enabled: false
      discovery:
        enabled: false
        register-enabled: false
      config:
        enabled: false
        import-check:
          enabled: false
        shared-configs: []
        extension-configs: []
    openfeign:
      circuitbreaker:
        enabled: false
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:{db};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE
    username: sa
    password:
    hikari:
      maximum-pool-size: 4
      minimum-idle: 1
      connection-timeout: 3000
  sql:
    init:
      mode: never
  jpa:
    hibernate:
      ddl-auto: none
  data:
    redis:
      host: 127.0.0.1
      port: 63790
      connect-timeout: 50
      timeout: 50
{EXCLUDES_SERVLET}
  flyway:
    enabled: false
  liquibase:
    enabled: false

knife4j:
  enable: false
  production: true
springdoc:
  api-docs:
    enabled: false

seata:
  enabled: false

feign:
  sentinel:
    enabled: false
  okhttp:
    enabled: false
  httpclient:
    enabled: false

management:
  endpoints:
    web:
      exposure:
        include: info,health
  health:
    redis:
      enabled: false
    db:
      enabled: false
    nacos:
      enabled: false

mybatis-plus:
  configuration:
    cache-enabled: false
  global-config:
    banner: false
    db-config:
      id-type: auto
  mapper-locations: classpath*:mapper/**/*.xml

logging:
  level:
    root: WARN
    com.lianshengtong: INFO
    org.springframework.cloud.context: ERROR
    com.alibaba.cloud.nacos: ERROR
    org.redisson: ERROR
    io.seata: ERROR
"""

SERVLETS = [
    ("lsc-ledger-service",        "lsc-ledger-itest",       "lsc_ledger_itest"),
    ("lsc-order-service",         "lsc-order-itest",        "lsc_order_itest"),
    ("lsc-user-service",          "lsc-user-itest",         "lsc_user_itest"),
    ("lsc-promotion-service",     "lsc-promo-itest",        "lsc_promo_itest"),
    ("lsc-mall-service",          "lsc-mall-itest",         "lsc_mall_itest"),
    ("lsc-reconciliation-service","lsc-recon-itest",        "lsc_recon_itest"),
    ("lsc-evidence-service",      "lsc-evid-itest",         "lsc_evid_itest"),
    ("lsc-admin-service",         "lsc-admin-itest",        "lsc_admin_itest"),
]

for mod, app, db in SERVLETS:
    path = os.path.join(mod, "src/test/resources/application-itest.yml")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(servlet_template(app, db))

GATEWAY_YML = """server:
  port: 0

spring:
  main:
    web-application-type: reactive
    banner-mode: off
    allow-bean-definition-overriding: true
  application:
    name: lsc-gateway-itest
  cloud:
    bootstrap:
      enabled: false
    nacos:
      enabled: false
      discovery:
        enabled: false
        register-enabled: false
      config:
        enabled: false
        import-check:
          enabled: false
        shared-configs: []
        extension-configs: []
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: itest-ping
          uri: no://op
          predicates:
            - Path=/itest-ping
    loadbalancer:
      ribbon:
        enabled: false
  autoconfigure:
    exclude:
      - com.alibaba.cloud.nacos.NacosConfigAutoConfiguration
      - com.alibaba.cloud.nacos.endpoint.NacosConfigEndpointAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration
      - com.alibaba.cloud.nacos.endpoint.NacosDiscoveryEndpointAutoConfiguration
      - com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration
      - com.alibaba.cloud.nacos.NacosServiceAutoConfiguration
      - com.alibaba.cloud.nacos.loadbalancer.LoadBalancerNacosAutoConfiguration
      - org.springframework.cloud.autoconfigure.RefreshAutoConfiguration
      - org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration
      - org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration
  data:
    redis:
      host: 127.0.0.1
      port: 63790
      connect-timeout: 50
      timeout: 50

knife4j:
  enable: false
springdoc:
  api-docs:
    enabled: false

management:
  endpoints:
    web:
      exposure:
        include: info,health
  health:
    redis:
      enabled: false

logging:
  level:
    root: WARN
    com.lianshengtong: INFO
    com.alibaba.cloud.nacos: ERROR
"""

path = "lsc-gateway/src/test/resources/application-itest.yml"
os.makedirs(os.path.dirname(path), exist_ok=True)
with open(path, "w") as f:
    f.write(GATEWAY_YML)
print("GENERATED OK")
