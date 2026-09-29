# ---------------------------------------------------------------------------
# 部署到云平台时用这个 Dockerfile。
# 第一阶段负责编译，第二阶段只保留运行需要的东西，镜像更小。
# ---------------------------------------------------------------------------

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p out \
    && javac -encoding UTF-8 -d out src/*.java \
    && jar --create --file gomoku-server.jar --main-class GomokuServer -C out .

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/gomoku-server.jar .
# 网页前端必须一起带上，否则页面会 404
COPY web ./web

# 云平台一般通过 PORT 环境变量指定端口，程序会自动读取
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "gomoku-server.jar"]
