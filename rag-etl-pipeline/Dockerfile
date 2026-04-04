# ==========================================
# 第一阶段：构建阶段 (Builder)
# ==========================================
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /build

# 复制 pom.xml 和源码
COPY pom.xml .
COPY src ./src

# 执行打包命令
RUN mvn clean package -DskipTests

# ==========================================
# 第二阶段：运行阶段 (Runtime)
# ==========================================
FROM amazoncorretto:21-alpine
WORKDIR /app

# 从第一阶段提取生成的 jar 包
COPY --from=builder /build/target/rag-etl-pipeline-1.0.0-SNAPSHOT.jar ./app.jar

# 暴露应用端口
EXPOSE 10201

# 针对 2核2G 服务器设置严格的 JVM 内存限制
# 初始堆 256M，最大堆 384M，元空间 96M，使用 G1 垃圾回收器
# 保留约 1.4G 给操作系统 + PGVector 连接 + ETL 批处理线程栈
ENV JAVA_OPTS="-Xms256m -Xmx384m -XX:MaxMetaspaceSize=96m -XX:+UseG1GC"

CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
