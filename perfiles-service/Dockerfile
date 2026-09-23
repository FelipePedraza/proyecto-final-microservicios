FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/perfiles-service ./cmd/server

FROM alpine:3.20
RUN addgroup -S -g 10001 appgroup && adduser -S -D -H -u 10001 -G appgroup appuser
COPY --from=build /out/perfiles-service /perfiles-service
USER 10001:10001
EXPOSE 8083
ENTRYPOINT ["/perfiles-service"]
