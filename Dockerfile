# ============================================================
# Claw 助手 (微信 AI 机器人) — Docker 镜像
# 多阶段构建: Maven 编译 → JRE 21 精简运行镜像
#
# 构建:  docker build -t claw-assistant .
# 运行:  docker compose up -d && docker compose logs -f
# ============================================================

# ---------- 阶段 1: 编译 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 阿里云 Maven 镜像加速 (容器内全量下载依赖, 国内直连 Maven Central 慢)
COPY docker/settings.xml /root/.m2/settings.xml

# 先拷贝 pom, 利用 Docker 层缓存避免每次重复下载依赖
COPY pom.xml ./
RUN mvn -q dependency:go-offline || true

# 再拷贝源码并打包
COPY src ./src
# 用脱敏配置覆盖真实 application.properties (密钥不进镜像, 运行时通过环境变量注入)
COPY docker/application.properties src/main/resources/application.properties
RUN mvn -q -DskipTests package

# ---------- 阶段 2: 运行 ----------
FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.title="Claw Assistant" \
      org.opencontainers.image.description="基于 iLink 协议的微信 AI 机器人 (DeepSeek + Function Calling)" \
      org.opencontainers.image.source="https://github.com/Constantglow/youkeda_pj"

WORKDIR /app

# 时区: 微信机器人与定时任务依赖本地时间
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

# 数据目录约定 (建议挂载 volume 持久化):
#   /app/data          -> 会话持久化 (wechat.session.persist-dir=./data/sessions) + 聊天历史 (sqlite.db.path)
#   /app/home          -> user.home (ChatHistoryService 写 ~/.youkeda/chat.db, 重定向到挂载卷)
#   tasks.db           -> 定时任务库, 符号链接到 /app/data/tasks.db 一并持久化
RUN mkdir -p /app/data /app/home && ln -sf /app/data/tasks.db /app/tasks.db

COPY --from=build /build/target/*.jar /app/app.jar

# --login: 容器启动后自动发起微信扫码登录, 二维码 URL 打印到 stdout, 用 docker logs 查看
ENTRYPOINT ["java", "-Duser.home=/app/home", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-jar", "/app/app.jar", "--login"]
