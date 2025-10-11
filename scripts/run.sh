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

if ! docker ps | grep -q cryptopedia_mysql; then
    docker-compose up -d mysql
    sleep 10
fi

docker exec cryptopedia_mysql sh -c "mysql -ucryptopedia_user -pcryptopedia_password -D cryptopedia -e 'SELECT 1;'" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "Database connection failed. Run setup.sh first."
    exit 1
fi

ENVIRONMENT=$ENVIRONMENT cargo run
