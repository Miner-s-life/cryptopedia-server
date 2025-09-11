#!/bin/bash

# Default environment
ENVIRONMENT=${1:-dev}

# Check if environment file exists
ENV_FILE=".env.${ENVIRONMENT}"
if [ ! -f "$ENV_FILE" ]; then
    echo "Environment file $ENV_FILE not found."
    echo "Available environments:"
    ls .env.* 2>/dev/null | sed 's/.env./  - /' || echo "  No environment files found"
    echo ""
    echo "Usage: $0 [environment]"
    echo "Example: $0 dev"
    echo "         $0 prod"
    exit 1
fi

echo "Starting with environment: $ENVIRONMENT"

if ! docker ps | grep -q cryptopedia_db; then
    docker-compose up -d postgres
    sleep 10
fi

docker exec -e PGPASSWORD=cryptopedia_password cryptopedia_db psql -U cryptopedia_user -d cryptopedia -c "SELECT 1;" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "Database connection failed. Run setup.sh first."
    exit 1
fi

ENVIRONMENT=$ENVIRONMENT cargo run
