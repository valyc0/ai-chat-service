# ChatClient e Advisor — Spring AI

## Architettura

```
ChatService
  │
  ├── ChatClientWrapper.prompt()
  │       │
  │       └── ChatClient.prompt()           ← Spring AI SDK
  │               │
  │               ├── MessageChatMemoryAdvisor  ← intercetta prima/dopo
  │               │       │
  │               │       ├── PRIMA: ChatMemory.get() → carica cronologia
  │               │       └── DOPO:  ChatMemory.add() → salva nuovi messaggi
  │               │
  │               └── ChatClient.ChatClientRequestSpec.call()
  │                       │
  │                       └── HTTP request → NVIDIA API (OpenAI-compatible)
  │
  └── AiClient.call(spec)                    ← resilience (retry + circuit breaker)
```

## ChatClient

Spring AI fornisce `ChatClient` come interfaccia fluida per interagire con modelli AI. È simile a `WebClient` o `RestTemplate`, ma per LLM.

### Creazione del bean

In `AiChatApplication.java`:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
    return builder
            .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
            .build();
}
```

`ChatClient.Builder` è fornito automaticamente da Spring AI grazie a `spring-ai-openai-spring-boot-starter`. Legge le proprietà `spring.ai.openai.*` e configura:
- `base-url` → `https://integrate.api.nvidia.com`
- `api-key` → `${NVIDIA_API_KEY}`
- `connect-timeout` → `30s`
- `read-timeout` → `60s`
- `chat.options.model` → `meta/llama-3.2-3b-instruct`
- `chat.options.temperature` → `0.7`

Il builder produce un `ChatClient` singleton con un advisor predefinito (tutte le chiamate avranno memoria).

### ChatClientWrapper

```java
@Component
public class ChatClientWrapper {
    private final ChatClient chatClient;

    public ChatClient.ChatClientRequestSpec prompt() {
        return chatClient.prompt();
    }
}
```

Wrapper `@Component` attorno a `ChatClient`. Serve solo per rendere testabile il codice: `ChatClient` è un bean Spring AI che non può essere facilmente mockato via `@MockBean` perché è costruito con un builder. `ChatClientWrapper` è una classe nostra, sostituibile con un mock nei test.

### Fluent API

`ChatClient` usa un builder pattern:

```java
chatClient.prompt()                           // → ChatClientRequestSpec
    .user("testo utente")                     // aggiunge UserMessage
    .system("system prompt")                  // aggiunge SystemMessage
    .advisors(a -> a.param("chiave", "valore")) // parametri per advisor
    .call()                                   // → ChatResponse
    .content();                               // → String (testo della risposta)
```

`ChatClientRequestSpec` accumula tutta la request (messaggi, parametri, advisors) e al `.call()` invia tutto al modello AI.

## Advisor

Gli advisor sono intercettori che Spring AI esegue **prima** e **dopo** ogni chiamata al modello. Seguono il pattern **Chain of Responsibility**: prima della chiamata possono modificare la request, dopo possono processare la response.

```
     prompt() ──▶ Advisor 1 ──▶ Advisor 2 ──▶ ... ──▶ call()
                       │                            ▲
                       │  before()                   │ after()
                       ▼                            │
                 modifica request              processa response
```

### MessageChatMemoryAdvisor

L'unico advisor configurato in questo progetto:

```java
.defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
```

**Prima della chiamata (`before()`):**
1. Legge il parametro `chat_memory_conversation_id` dalla request
2. Chiama `chatMemory.get(conversationId, ...)` per recuperare i messaggi precedenti
3. Prepend i messaggi recuperati alla lista dei messaggi da inviare al modello
4. In questo modo l'AI vede tutto lo storico della conversazione e può rispondere coerentemente

**Dopo la chiamata (`after()`):**
1. Prende il messaggio utente e la risposta dell'assistente appena generata
2. Chiama `chatMemory.add(conversationId, messages)` per salvarli
3. La prossima volta che arriva una richiesta con la stessa `conversationId`, il modello vedrà anche questi messaggi

### Parametro `chat_memory_conversation_id`

In `ChatService`:

```java
spec = spec.advisors(a -> a.param("chat_memory_conversation_id", convId));
```

`MessageChatMemoryAdvisor` cerca il parametro `chat_memory_conversation_id` nella request spec per sapere quale conversazione caricare/salvare. Se non viene fornito, l'advisor non fa nulla (chat senza memoria).

## ChatMemory

Interfaccia Spring AI per la persistenza dei messaggi:

| Metodo | Firma | Quando viene chiamato |
|--------|-------|----------------------|
| `get` | `get(conversationId, lastN)` | `before()` dell'advisor |
| `add` | `add(conversationId, messages)` | `after()` dell'advisor |
| `clear` | `clear(conversationId)` | esplicitamente (non usato qui) |

### RedisChatMemory (nostra implementazione)

```java
@Component
public class RedisChatMemory implements ChatMemory { ... }
```

**Formato in Redis:**

```
Key:   chat:memory:{conversationId}    (es. chat:memory:conv-1)
Type:  LIST (Redis List)
Value: JSON {"role":"user","content":"Ciao"}
       JSON {"role":"assistant","content":"Ciao, come posso aiutarti?"}

TTL:   4h (configurabile via chat.memory.ttl)
```

Ogni messaggio è un JSON su 2 campi:
- `role`: `"user"`, `"assistant"`, o `"system"`
- `content`: il testo del messaggio

**Serializzazione**: un `ObjectMapper` converte il `MessageRecord` in JSON. Il `StringRedisTemplate` gestisce la connessione Lettuce a Redis.

**Sanitizzazione**: il `conversationId` viene ripulito con `[^a-zA-Z0-9_-]` per prevenire injection su Redis (es. chiavi con `..` o spazi).

**I messaggi sono append-only**: ogni nuovo scambio viene aggiunto in coda (`rightPush`). Al recupero, `redis.opsForList().range(key, -lastN, -1)` prende gli ultimi N messaggi.

## Flusso completo

```
POST /api/chat {"conversationId":"abc","q":"Come mi chiamo?"}

1. ChatController riceve la request, la passa a ChatService
2. ChatService:
   a. sanitizza conversationId → "abc"
   b. chatClient.prompt().user("Come mi chiamo?")
   c. .advisors(a -> a.param("chat_memory_conversation_id", "abc"))
3. ChatClientRequestSpec.call() → MessageChatMemoryAdvisor.before():
   a. Legge conversationId="abc"
   b. RedisChatMemory.get("abc", ...) → [{"role":"user","content":"Mi chiamo Marco"}]
   c. Prepend il messaggio storico alla lista
4. La request ora contiene: [UserMessage("Mi chiamo Marco"), UserMessage("Come mi chiamo?")]
5. HTTP POST a NVIDIA API con tutti i messaggi
6. L'AI risponde: "Ti chiami Marco!"
7. MessageChatMemoryAdvisor.after():
   a. RedisChatMemory.add("abc", [UserMessage("Come mi chiamo?"), AssistantMessage("Ti chiami Marco!")])
8. La response torna al client: {"reply":"Ti chiami Marco!", ...}
```

## Perché un advisor invece di gestire la memoria a mano?

| Approccio | Vantaggio |
|-----------|-----------|
| Advisor | Separazione delle responsabilità — ChatService costruisce la request, l'advisor gestisce la cronologia |
| Advisor | Riutilizzabile — stesso advisor funziona con qualsiasi modello |
| Manuale | Dovresti chiamare RedisChatMemory.get/add esplicitamente ogni volta |
| Manuale | Dovresti fare merge manuale dei messaggi storici con quelli nuovi |
