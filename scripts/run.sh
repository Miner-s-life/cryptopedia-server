#!/bin/bash

if [ ! -f ".env" ]; then
    echo ".env file not found. Create one: cp .env.example .env"
    exit 1
fi

if ! docker ps | grep -q cryptopedia_db; then
    docker-compose up -d postgres
    sleep 10
fi

docker exec -e PGPASSWORD=cryptopedia_password cryptopedia_db psql -U cryptopedia_user -d cryptopedia -c "SELECT 1;" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "Database connection failed. Run setup.sh first."
    exit 1
fi

cargo run
