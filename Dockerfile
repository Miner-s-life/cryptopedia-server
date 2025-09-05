# Rust 애플리케이션용 Dockerfile (향후 사용)
FROM rust:1.75 as builder

WORKDIR /app
COPY Cargo.toml Cargo.lock ./
COPY src ./src

# 릴리즈 빌드
RUN cargo build --release

# 런타임 이미지
FROM debian:bookworm-slim

# 필요한 패키지 설치
RUN apt-get update && apt-get install -y \
    ca-certificates \
    libssl3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 빌드된 바이너리 복사
COPY --from=builder /app/target/release/crypto-arbitrage-backend /app/

# 환경 변수 설정
ENV RUST_LOG=info
ENV SERVER_HOST=0.0.0.0
ENV SERVER_PORT=8080

EXPOSE 8080

CMD ["./crypto-arbitrage-backend"]
