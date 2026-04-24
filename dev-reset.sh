#!/bin/bash

set -e

echo "🧹 Stopping existing containers..."
docker-compose down --volumes --remove-orphans || true

echo "🔍 Killing any containers using key ports (5432, 8082, 4550)..."
for port in 5432 8082 4550; do
  CONTAINER_IDS=$(docker ps -q --filter "publish=$port")
  if [ ! -z "$CONTAINER_IDS" ]; then
    echo "⚠️  Killing containers on port $port: $CONTAINER_IDS"
    docker rm -f $CONTAINER_IDS
  fi
done

echo "🔨 Building application (Gradle)..."
./gradlew clean build

echo "✅ Environment is ready!"
echo "👉 App: http://localhost:4550"
echo "👉 WireMock: http://localhost:8082"
