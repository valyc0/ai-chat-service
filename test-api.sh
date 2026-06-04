#!/bin/bash
BASE_URL="${API_URL:-http://localhost:8080}"
OK=0
TOTAL=0

check() {
  local desc="$1" data="$2"
  TOTAL=$((TOTAL + 1))
  echo ""
  echo "--- $desc ---"
  echo "   ➤ $data"
  http_code=$(curl -s -o /tmp/api-resp.json -w "%{http_code}" -X POST "$BASE_URL/api/chat" \
    -H "Content-Type: application/json" -d "$data")
  echo "   ← HTTP $http_code"
  if [ "$http_code" = "200" ] || [ "$http_code" = "400" ] || [ "$http_code" = "401" ]; then
    OK=$((OK + 1))
  fi
}

echo "============================================"
echo "  AI Chat API - Test Suite (curl)"
echo "  Base URL: $BASE_URL"
echo "============================================"

check "Valid request with all fields" \
  '{"conversationId":"test-1","prompt":"Sei un assistente italiano","q":"Ciao, chi sei?"}'

check "Without prompt (optional)" \
  '{"conversationId":"test-2","q":"Che ore sono?"}'

check "Without conversationId" \
  '{"q":"Ciao, come stai?"}'

check "Missing q (should be 400)" \
  '{"conversationId":"test-4","prompt":"AI"}'

check "Blank conversationId" \
  '{"conversationId":"","q":"Ciao"}'

check "Invalid JSON (should be 400)" \
  'not json'

check "Blank q (should be 400)" \
  '{"q":""}'

echo ""
echo "--- 8. Conversation history ---"
echo "   ➤ Message 1: Ricorda che mi chiamo Marco"
TOTAL=$((TOTAL + 1))
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"test-history","prompt":"Sei un assistente","q":"Ricorda che mi chiamo Marco"}' \
  | grep -E '(HTTP|ricorda|Marco)'
OK=$((OK + 1))
echo "   ➤ Message 2: Come mi chiamo?"
TOTAL=$((TOTAL + 1))
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"test-history","q":"Come mi chiamo?"}' \
  | grep -E '(HTTP|Marco)'
OK=$((OK + 1))

echo ""
echo "============================================"
echo "  Risultati: $OK / $TOTAL richieste OK"
echo "============================================"
