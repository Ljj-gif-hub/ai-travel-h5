FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 国内服务器无法访问 Maven Central，使用阿里云镜像（settings.xml 一并打包）
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 3200
CMD ["java", "-jar", "app.jar", "--server.port=3200"]
