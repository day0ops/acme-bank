FROM golang:1.27-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /accounts ./cmd/server

FROM gcr.io/distroless/static-debian12
COPY --from=build /accounts /accounts
EXPOSE 8080 9090
ENTRYPOINT ["/accounts"]
