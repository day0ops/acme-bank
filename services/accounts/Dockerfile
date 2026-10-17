# Build from the repo root: docker build -f services/accounts/Dockerfile .
FROM golang:1.27-alpine AS build
WORKDIR /src
COPY proto/gen/go ./proto/gen/go
COPY services/accounts ./services/accounts
WORKDIR /src/services/accounts
RUN go mod download
RUN CGO_ENABLED=0 go build -o /accounts ./cmd/server

FROM gcr.io/distroless/static-debian12
COPY --from=build /accounts /accounts
EXPOSE 8080 9090
ENTRYPOINT ["/accounts"]
