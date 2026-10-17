# Build from the repo root: docker build -f services/dashboard-bff/Dockerfile .
FROM golang:1.27-alpine AS build
WORKDIR /src
COPY proto/gen/go ./proto/gen/go
COPY services/dashboard-bff ./services/dashboard-bff
WORKDIR /src/services/dashboard-bff
RUN go mod download
RUN CGO_ENABLED=0 go build -o /dashboard-bff ./cmd/server

FROM gcr.io/distroless/static-debian12
COPY --from=build /dashboard-bff /dashboard-bff
EXPOSE 8080
ENTRYPOINT ["/dashboard-bff"]
