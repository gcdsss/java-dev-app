FROM openjdk:17-jdk-alpine

# 安装curl用于健康检查
RUN apk add --no-cache curl

# 设置工作目录
WORKDIR /app

# 复制JAR文件
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# 暴露端口
EXPOSE 8000

# 设置时区
ENV TZ=Asia/Shanghai

# 运行应用
ENTRYPOINT ["java", "-jar", "app.jar"]