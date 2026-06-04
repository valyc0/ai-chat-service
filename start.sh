#!/bin/sh
set -e

MODE="${1:-dev}"

if [ "$MODE" = "prod" ]; then
  docker compose up -d --build
else
  docker compose -f docker-compose.dev.yml up -d
  mvn spring-boot:run
fi
