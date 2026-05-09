FROM golang:1.22-alpine AS builder
WORKDIR /build
COPY go.mod ./
COPY src/ ./src/
RUN go build -o server ./src/

FROM alpine:3.19
WORKDIR /app
COPY --from=builder /build/server .
EXPOSE 3003
CMD ["./server"]
