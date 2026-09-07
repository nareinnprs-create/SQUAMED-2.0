#!/bin/bash
# Stop the SQUAMED local development infrastructure
set -e

COMPOSE_FILE="infrastructure/docker/docker-compose.yml"

echo "Stopping SQUAMED development infrastructure..."
docker compose -f "$COMPOSE_FILE" down "$@"
echo "Done."