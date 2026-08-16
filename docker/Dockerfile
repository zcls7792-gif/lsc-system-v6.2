# ============================================================
# 链盛通LSC系统 - 统一Dockerfile
# 基于 eclipse-temurin:17-jre 轻量运行时
# ============================================================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="LSC Project Team"
LABEL version="6.2.0-AI"

# 时区设置
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 应用参数
ARG JAR_FILE
ARG SERVICE_NAME
ARG SERVER_PORT=8080
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom"
ENV SERVICE_NAME=${SERVICE_NAME}
ENV SERVER_PORT=${SERVER_PORT}

WORKDIR /app

# 复制jar包
COPY target/${JAR_FILE} app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=90s \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

EXPOSE ${SERVER_PORT}

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --spring.application.name=${SERVICE_NAME} --server.port=${SERVER_PORT}"]
