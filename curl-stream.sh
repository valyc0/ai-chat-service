#!/bin/bash
BASE_URL="${API_URL:-http://localhost:8080}"

echo "=== Chat Streaming ==="
echo "Base URL: $BASE_URL"
echo ""

# 1) Chat semplice in streaming
echo "--- 1. Chat semplice (streaming) ---"
curl -s -N -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"q":"Ciao, chi sei?"}'
echo ""

# 2) Con conversationId e system prompt
echo "--- 2. Con memoria e prompt ---"
curl -s -N -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"test-1","prompt":"Sei un assistente italiano","q":"Ciao, chi sei?"}'
echo ""

# 3) Errore validazione (manca q)
echo "--- 3. Errore: q mancante (400) ---"
curl -s -N -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"bad"}'
echo ""
