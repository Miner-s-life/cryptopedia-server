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

- `GET /api/v1/arbitrage/{symbol}?from={exchange}&to={exchange}` - Get directional arbitrage analysis

## Tech Stack

- Rust + Actix-web
- PostgreSQL + SQLx
- Docker & Docker Compose