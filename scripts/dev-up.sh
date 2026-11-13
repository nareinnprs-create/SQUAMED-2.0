#!/bin/bash
# Start the SQUAMED local development infrastructure
set -e

COMPOSE_FILE="infrastructure/docker/docker-compose.yml"

echo "Starting SQUAMED development infrastructure..."
docker compose -f "$COMPOSE_FILE" up -d

echo ""
echo "Waiting for services to become healthy..."
for service in postgres redis kafka opensearch minio temporal prometheus grafana temporal-ui; do
  status=""
  for i in $(seq 1 30); do
    status=$(docker inspect --format='{{.State.Health.Status}}' "squamed-$service" 2>/dev/null || echo "creating")
    if [ "$status" = "healthy" ]; then
      break
    fi
    sleep 2
  done
  if [ "$status" = "healthy" ]; then
    echo "  ✓ $service healthy"
  else
    echo "  ✗ $service status: $status"
  fi
done

echo ""
echo "Creating Kafka topics..."
bash infrastructure/docker/kafka/create-topics.sh

echo ""
echo "Initializing MinIO buckets..."
docker exec squamed-minio sh -c '
  mc alias set local http://localhost:9000 squamed squamed_dev_password > /dev/null 2>&1
  mc mb -p local/squamed-documents > /dev/null 2>&1 || true
  mc mb -p local/squamed-imaging > /dev/null 2>&1 || true
  mc mb -p local/squamed-exports > /dev/null 2>&1 || true
  mc mb -p local/squamed-ai > /dev/null 2>&1 || true
  echo "  ✓ Buckets created"
'

echo ""
echo "SQUAMED development infrastructure is ready:"
echo "  PostgreSQL:   localhost:5433 (squamed/squamed_dev_password)"
echo "  Redis:        localhost:6380"
echo "  Kafka:        localhost:9092"
echo "  OpenSearch:   localhost:9200"
echo "  MinIO:        localhost:9000 (console: localhost:9001)"
echo "  Temporal:     localhost:7233 (UI: localhost:8088)"
echo "  Prometheus:   localhost:9090"
echo "  Grafana:      localhost:3005 (squamed/squamed_dev_password)"