# =============================================================================
# LSC Platform - 分层 Dockerfile（配合 spring-boot-maven-plugin layers）
# 构建前提：已执行 mvn package 生成 target/lsc-api-7.7.2-SNAPSHOT.jar
# 构建命令：docker build -t lsc-platform:7.7.2 -f lsc-api/Dockerfile .
# =============================================================================

# ---------- 阶段1：提取分层 ----------
# 使用已构建好的 fat jar，用 layertools 拆分为 4 个目录
FROM eclipse-temurin:17-jre-jammy AS extractor
WORKDIR /build
COPY lsc-api/target/lsc-api-7.7.2-SNAPSHOT.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ---------- 阶段2：运行时镜像 ----------
# 按层复制：变化频率低的在前，最大化 Docker 层缓存复用
FROM eclipse-temurin:17-jre-jammy
LABEL org.opencontainers.image.title="lsc-platform" \
      org.opencontainers.image.version="7.7.2" \
      org.opencontainers.image.description="链盛通消费回馈权益系统后端"

WORKDIR /app

# 时区（中文环境必需）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 4 层复制（顺序很重要：低频层在前，高频层在后）
COPY --from=extractor /build/dependencies/ ./
COPY --from=extractor /build/spring-boot-loader/ ./
COPY --from=extractor /build/snapshot-dependencies/ ./
COPY --from=extractor /build/application/ ./

# 微信小程序凭证通过运行时环境变量注入（不入镜像）
ENV WX_MP_APP_ID="" \
    WX_MP_APP_SECRET="" \
    WX_MP_TOKEN="" \
    WX_MP_AES_KEY="" \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

EXPOSE 8080

# spring-boot-loader v3 入口类
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
