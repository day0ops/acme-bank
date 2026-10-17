# Build from the repo root: docker build -f services/fraud-risk/Dockerfile .
# Debian-based (not alpine) build image: the protobuf Gradle plugin downloads a
# glibc-linked protoc-gen-grpc-java binary that fails to exec on musl-libc Alpine.
FROM gradle:8.10-jdk21 AS build
WORKDIR /src
COPY proto ./proto
COPY services/fraud-risk ./services/fraud-risk
WORKDIR /src/services/fraud-risk
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /src/services/fraud-risk/build/libs/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
