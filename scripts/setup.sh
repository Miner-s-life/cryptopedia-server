#!/bin/bash

if ! command -v docker &> /dev/null; then
    echo "Docker is not installed."
    echo "Install Docker Desktop: https://www.docker.com/products/docker-desktop"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "Docker Compose is not installed."
    exit 1
fi

if ! command -v cargo &> /dev/null; then
    echo "Rust is not installed."
    echo "Install Rust: https://rustup.rs/"
    exit 1
fi

docker-compose up -d mysql
sleep 10
docker exec cryptopedia_mysql sh -c "mysql -uroot -prootpassword -e 'SELECT 1;'" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "Database connection failed."
    exit 1
fi

docker exec cryptopedia_mysql sh -c "mysql -uroot -prootpassword -D cryptopedia -e 'SHOW TABLES;'" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "Migration failed."
    exit 1
fi

TABLE_COUNT=$(docker exec cryptopedia_mysql sh -c "mysql -uroot -prootpassword -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='cryptopedia';\"" | tr -d ' \n\r')

if [ -z "$TABLE_COUNT" ] || [ "$TABLE_COUNT" -le 0 ] 2>/dev/null; then
    echo "Table creation failed."
    exit 1
fi

echo "Setup complete. Run: cargo run"
