# Alternativa ao Pterodactyl: docker build -t zentro . && docker run -p 8080:8080 -v zentro-data:/app/data zentro
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon :server:shadowJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/libs/zentro-server.jar /app/zentro-server.jar
ENV ZENTRO_DATA_DIR=/app/data PORT=8080
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=85.0", "-XX:+UseSerialGC", "-jar", "/app/zentro-server.jar"]
