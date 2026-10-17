# Build from the repo root: docker build -f services/customers/Dockerfile .
# Debian-based (not alpine) build image: the protobuf Gradle plugin downloads a
# glibc-linked protoc-gen-grpc-java binary that fails to exec on musl-libc Alpine.
FROM gradle:8.10-jdk21 AS build
WORKDIR /src
COPY proto ./proto
COPY services/customers ./services/customers
WORKDIR /src/services/customers
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /src/services/customers/build/libs/*.jar /app.jar
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app.jar"]
