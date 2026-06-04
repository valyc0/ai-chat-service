#!/bin/sh
set -e

MODE="${1:-dev}"

# Carica .env se esiste (Docker lo fa automaticamente, mvn spring-boot:run no)
if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

# Verifica NVIDIA_API_KEY
if [ -z "$NVIDIA_API_KEY" ] || echo "$NVIDIA_API_KEY" | grep -q '^nvapi-xxxxxxxx'; then
  echo "=================================================="
  echo "  ERRORE: NVIDIA_API_KEY non configurata!"
  echo ""
  echo "  Crea un file .env nella root del progetto con:"
  echo "    NVIDIA_API_KEY=nvapi-la-tua-chiave-vera"
  echo ""
  echo "  Oppure esportala nell'ambiente:"
  echo "    export NVIDIA_API_KEY=nvapi-la-tua-chiave-vera"
  echo "=================================================="
  exit 1
fi

if [ "$MODE" = "prod" ]; then
  docker compose up -d --build
else
  docker compose -f docker-compose.dev.yml up -d
  mvn spring-boot:run
fi
