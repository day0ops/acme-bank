# Build from the repo root: docker build -f services/cards/Dockerfile .
FROM rust:1.90-slim-bookworm AS build
RUN apt-get update && apt-get install -y protobuf-compiler && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY proto ./proto
COPY services/cards ./services/cards
WORKDIR /src/services/cards
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/services/cards/target/release/cards-server /cards-server
EXPOSE 8080 9090
ENTRYPOINT ["/cards-server"]
