# AI Chat Service

Microservizio Spring Boot AI con NVIDIA API e Redis per conversazioni con memoria persistente.

## Architettura

```
POST /api/chat ──▶ ChatController ──▶ ChatService ──▶ AiClient (retry ×3 + circuit breaker)
                                                          │
                                                          └──▶ ChatClientWrapper
                                                                  │
                                                                  └──▶ ChatClient (Spring AI)
                                                                          │
                                                                          └──▶ NVIDIA API
                                                                               (llama-3.2-3b)

ChatService ──▶ MessageChatMemoryAdvisor
                        │
                        └──▶ RedisChatMemory (TTL configurable, default 4h)
```

- **ChatController**: validazione input via `@Valid`
- **ChatService**: orchestrazione con logging e MDC
- **AiClient**: resilience (retry ×3 con exponential backoff + circuit breaker + fallback)
- **ChatClientWrapper**: wrapper `@Component` per facilitare il mocking nei test
- **RedisChatMemory**: implementazione `ChatMemory` via Redis (JSON, TTL configurabile)
- **GlobalExceptionHandler**: errori strutturati (400/500), nessuno stack trace in response

## Prerequisiti

- Java 17+, Maven
- Docker (per Redis)
- Chiave API NVIDIA ([NVAI API](https://build.nvidia.com/explore/discover))

## Avvio rapido

```bash
# 1. Copia e configura la chiave API NVIDIA
cp .env-example .env
# modifica .env con la tua NVIDIA_API_KEY

# 2. Sviluppo (solo Redis via Docker, Java via Maven)
./start.sh

# Produzione (Redis + app containerizzati)
./start.sh prod
```

Il servizio parte su `http://localhost:8080`.

### Docker Compose

| File | Servizi | Uso |
|------|---------|-----|
| `docker-compose.dev.yml` | solo Redis | `docker compose -f docker-compose.dev.yml up -d` |
| `docker-compose.yml` | Redis + App | `docker compose up -d --build` |

## API

### `POST /api/chat`

**Request:**

| Campo           | Tipo   | Obbligatorio | Descrizione                              |
|-----------------|--------|-------------|------------------------------------------|
| `conversationId`| string | no          | ID conversazione (max 100 caratteri)     |
| `prompt`        | string | no          | System prompt (max 2000 caratteri)       |
| `q`             | string | sì          | Domanda / messaggio utente (max 10000 caratteri) |

**Response (200):**
```json
{
  "conversationId": "conv-1",
  "reply": "Ciao! Sono un assistente AI...",
  "model": "meta/llama-3.2-3b-instruct",
  "timestamp": "2026-06-04T21:00:00.000Z"
}
```

**Response (400 — validation error):**
```json
{
  "status": 400,
  "error": "Validation Error",
  "message": "q: q is required",
  "timestamp": "2026-06-04T21:00:00.000Z"
}
```

**Esempi curl:**

```bash
# Chat semplice
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"conv-1","prompt":"Sei un assistente italiano","q":"Ciao, chi sei?"}'

# Senza conversationId (chat senza memoria)
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"q":"Ciao, come stai?"}'

# Senza system prompt
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"conv-2","q":"Che ore sono?"}'

# Conversazione con memoria (stessa conversationId)
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"history-1","prompt":"Sei un assistente","q":"Ricorda che mi chiamo Marco"}'

curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"history-1","q":"Come mi chiamo?"}'
# → Risponde "Marco"

# Errori (400)
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"x"}'                   # → 400 (manca q)
```

## Health Check & Metrics

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

## Test

```bash
# Test unitari (JUnit 5 + MockMvc + Mockito)
mvn clean test

# Test curl automatici (8 scenari, richiede app avviata)
bash test-api.sh
```

## Configurazione

Le variabili d'ambiente possono essere impostate via `.env` (grazie a `spring-dotenv`) o come variabili d'ambiente reali.

### Variabili d'ambiente

| Chiave                | Default        | Descrizione                            |
|-----------------------|----------------|----------------------------------------|
| `NVIDIA_API_KEY`      | —              | Chiave API NVIDIA (obbligatoria)       |
| `REDIS_HOST`          | `localhost`    | Host Redis                             |
| `REDIS_PORT`          | `6379`         | Porta Redis                            |
| `chat.memory.ttl`     | `4h`           | Durata messaggi in Redis               |

### Spring AI / NVIDIA

| Proprietà                            | Default dev       | Default prod       | Descrizione                          |
|--------------------------------------|-------------------|--------------------|--------------------------------------|
| `spring.ai.openai.base-url`          | `https://integrate.api.nvidia.com` | ← | Endpoint NVIDIA API           |
| `spring.ai.openai.connect-timeout`   | `30s`             | `10s`              | Timeout connessione                  |
| `spring.ai.openai.read-timeout`      | `60s`             | `30s`              | Timeout lettura                      |
| `spring.ai.openai.chat.options.model` | `meta/llama-3.2-3b-instruct` | ← | Modello AI                     |
| `spring.ai.openai.chat.options.temperature` | `0.7`      | ←                  | Temperatura del modello              |

### Resilience4j

| Proprietà                        | Valore | Descrizione                                |
|----------------------------------|--------|--------------------------------------------|
| Retry max-attempts               | 3      | Tentativi massimi                          |
| Retry wait-duration              | 1s     | Attesa iniziale                            |
| Retry exponential-backoff-multiplier | 2  | Backoff esponenziale                       |
| Circuit breaker sliding-window-size | 10  | Finestra di monitoraggio                   |
| Circuit breaker failure-rate-threshold | 50% | Soglia di apertura                    |
| Circuit breaker wait-duration-in-open-state | 30s | Durata stato aperto               |
| Circuit breaker half-open calls  | 3      | Chiamate di prova in half-open             |

## Profili Spring

### `prod`

Attivabile via `SPRING_PROFILES_ACTIVE=prod` o `spring.profiles.active=prod`.

| Variazione                        | Dev        | Prod          |
|-----------------------------------|------------|---------------|
| Connect timeout                   | 30s        | 10s           |
| Read timeout                      | 60s        | 30s           |
| Logging `com.example`             | INFO       | WARN          |
| Health show-details               | when-authorized | never    |

## Error Handling

| Condizione                        | HTTP  | Response                        |
|-----------------------------------|-------|---------------------------------|
| `q` mancante o vuoto              | 400   | `Validation Error`              |
| JSON malformato                   | 400   | `Bad Request`                   |
| `conversationId` > 100 caratteri  | 400   | `Validation Error`              |
| `prompt` > 2000 caratteri         | 400   | `Validation Error`              |
| `q` > 10000 caratteri             | 400   | `Validation Error`              |
| Errore generico                   | 500   | `Internal Server Error`         |
| AI non disponibile (dopo retry)   | 200   | Fallback message testuale       |

## Stack

- Spring Boot 3.4.4 (Java 17)
- Spring AI 1.0.0-M6 (OpenAI-compatible)
- Resilience4j 2.1.0 (retry + circuit breaker)
- Spring Data Redis (Lettuce, pool connessioni)
- `me.paulschwarz:spring-dotenv` (lettura `.env`)
- Micrometer + Prometheus (metriche)
- Logstash Logback Encoder (JSON in prod)
- Redis 7-Alpine
- NVIDIA API (meta/llama-3.2-3b-instruct)
