# Cryptopedia

## Quick Start

```bash
# Setup environment
./scripts/setup.sh

# Run server
./scripts/run.sh [environment]

# Examples
./scripts/run.sh        # default: dev
./scripts/run.sh dev    # development
./scripts/run.sh prod   # production
```

## API Endpoints

- `GET /api/v1/arbitrage` - Get arbitrage opportunities
- `GET /api/v1/kimchi-premium/{symbol}` - Get kimchi premium for symbol
- `GET /api/v1/prices/{symbol}` - Get exchange prices for symbol
- `GET /api/v1/fees/{symbol}/{amount}` - Calculate trading fees
- `GET /api/v1/exchange-rate` - Get current USD/KRW exchange rate

## Tech Stack

- Rust + Actix-web
- PostgreSQL + SQLx
- Docker & Docker Compose