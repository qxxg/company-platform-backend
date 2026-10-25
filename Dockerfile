# 公司管理系统后端运行镜像。
#
# 该镜像包含全部 Spring Cloud 微服务的可执行 JAR，由 docker-compose 通过
# SERVICE_JAR 为每个容器指定要启动的服务，从而复用同一层镜像、减少磁盘占用。
#
# 构建前先在开发机执行（JDK 27 + Maven 3.10）：
#   mvn -DskipTests package
# 再构建镜像：
#   docker build -t company-platform/backend:1.0.0 .
FROM eclipse-temurin:27-jre

LABEL org.opencontainers.image.title="company-platform-backend" \
      org.opencontainers.image.description="Company Platform Spring Cloud microservices" \
      org.opencontainers.image.version="1.0.0"

ARG APP_UID=10001
ARG APP_GID=10001

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom" \
    SERVICE_JAR=platform-gateway-1.0.0-SNAPSHOT.jar \
    SERVER_PORT=8080

# curl 用于容器健康检查；tzdata 保证 Asia/Shanghai 时区可用。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g "${APP_GID}" app \
    && useradd -u "${APP_UID}" -g app -m -s /usr/sbin/nologin app

WORKDIR /app

# 只复制可执行 JAR（Spring Boot 重新打包产物），不把源码和 .original 带进镜像。
COPY platform-*/target/*.jar /app/

RUN chown -R app:app /app

USER app

EXPOSE 8080

# SERVER_PORT 由 compose 按服务注入；只检查进程端口，完整业务健康由部署脚本验证。
HEALTHCHECK --interval=20s --timeout=5s --start-period=120s --retries=12 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/$SERVICE_JAR"]
