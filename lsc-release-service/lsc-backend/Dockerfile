# 多阶段构建：Maven 构建 → JRE 运行
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# 运行阶段
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/lsc-backend-1.0.0.jar app.jar
RUN mkdir -p /var/log/lsc
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java","-jar","-Xms256m","-Xmx512m","-Djava.security.egd=file:/dev/./urandom","app.jar"]
