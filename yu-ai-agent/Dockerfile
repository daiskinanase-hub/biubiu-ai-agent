# ==========================================
# 第一阶段：构建阶段 (Builder)
# 使用包含 Maven 的完整镜像进行代码编译
# ==========================================
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /build

# 复制 pom.xml 和源码
COPY pom.xml .
COPY src ./src

# 执行打包命令 (沿用您原本的命令，跳过测试)
RUN mvn clean package -DskipTests

# ==========================================
# 第二阶段：运行阶段 (Runtime)
# 使用轻量级基础镜像，仅包含运行所需的 JDK/JRE 环境
# ==========================================
FROM amazoncorretto:21-alpine
WORKDIR /app

# 从第一阶段提取生成的 jar 包，并重命名为 app.jar，极大减小镜像体积
COPY --from=builder /build/target/yu-ai-agent-0.0.1-SNAPSHOT.jar ./app.jar

# 暴露应用端口 (沿用您原本的端口)
EXPOSE 8123

# 【核心优化】：针对 2核2G 服务器设置严格的 JVM 内存限制
# 初始堆内存 256M，最大堆内存 384M，元空间限制 96M，使用 G1 垃圾回收器
ENV JAVA_OPTS="-Xms256m -Xmx384m -XX:MaxMetaspaceSize=96m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"

# 启动应用，将 JVM 参数动态注入
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]