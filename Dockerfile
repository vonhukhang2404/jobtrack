# =====================================================================
# JobTrack - Dockerfile（Vercel の Container Runtime 用）
#   ① build : Maven で jar を作る（テストは DB が必要なので、ここでは実行しない）
#   ② run   : JRE だけの小さいイメージで jar を実行する
# =====================================================================

# ---------- ① build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# 依存関係だけ先にダウンロード（pom.xml が変わらなければキャッシュが使われ、ビルドが速くなる）
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- ② run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 日本時間（LocalDateTime.now() や「今日」の判定を日本時間にする。サーバーは通常 UTC）
ENV TZ=Asia/Tokyo
# Vercel のプロジェクト設定でも PORT=8080 を指定する
ENV PORT=8080
# コンテナのメモリに合わせてヒープを決める・起動を少しでも速くする
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -Duser.timezone=Asia/Tokyo"

# root ではなく一般ユーザーで実行（セキュリティ）
RUN useradd --system --create-home app
USER app

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
