# Build from the repo root: docker build -f services/notifications/Dockerfile .
# Pinned to -bookworm explicitly (not the floating "slim" tag, which tracks
# Debian trixie as of this writing): the runtime stage below is
# debian:bookworm-slim, and a binary built against trixie's newer glibc
# fails to run on bookworm's older glibc ("GLIBC_2.38 not found").
FROM rust:1.90-slim-bookworm AS build
# protobuf-compiler: build.rs's prost_build step.
# g++/make (on top of the base image's gcc): with neither the "cmake-build"
# nor "dynamic-linking" feature enabled (Task 6's choice, matching what
# worked locally via Xcode CLT's clang/clang++), rdkafka-sys always
# vendor-builds librdkafka from its bundled source via configure/make, which
# needs a C++ compiler for librdkafka's C++ bindings in addition to gcc.
RUN apt-get update && apt-get install -y protobuf-compiler g++ make && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY proto ./proto
COPY services/notifications ./services/notifications
WORKDIR /src/services/notifications
RUN cargo build --release

FROM debian:bookworm-slim
# librdkafka is statically linked into the binary (see build-stage note
# above), so only a CA bundle is needed at runtime (JWKS fetch, OTLP export).
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/services/notifications/target/release/notifications-server /notifications-server
EXPOSE 8080
ENTRYPOINT ["/notifications-server"]
