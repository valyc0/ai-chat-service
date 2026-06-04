# Circuit Breaker — Resilience4j

Il circuito elettrico domestico: se passa troppa corrente, scatta e interrompe il flusso. Il circuit breaker software fa lo stesso con le chiamate remote: se l'AI risponde con troppi errori, il circuito "si apre" e blocca le richieste per un po', dando tempo al sistema di riprendersi.

## Stati

```
                  ┌──────────────────┐
                  │    CLOSED        │  ← tutto ok, le richieste passano
                  │  (chiuso)        │
                  └────────┬─────────┘
                           │ superata soglia errori (50%)
                           ▼
                  ┌──────────────────┐
                  │    OPEN          │  ← scattato, richieste bloccate
                  │  (aperto)        │     per 30 secondi
                  └────────┬─────────┘
                           │ scaduto wait-duration (30s)
                           ▼
                  ┌──────────────────┐
                  │  HALF_OPEN       │  ← prova a far passare 3 richieste
                  │ (semiaperto)     │     per vedere se l'AI è tornata ok
                  └────────┬─────────┘
                          ╱ ╲
                         ╱   ╲
                        ╱     ╲
                       ✔       ✘
                  (tutte ok)   (qualcuna fallisce)
                      │              │
                      ▼              ▼
                  CLOSED          OPEN
```

## Configurazione in `application.yml`

```yaml
resilience4j:
  circuitbreaker:
    instances:
      aiChat:
        sliding-window-size: 10                      # (1)
        failure-rate-threshold: 50                    # (2)
        wait-duration-in-open-state: 30s              # (3)
        permitted-number-of-calls-in-half-open-state: 3  # (4)
  retry:
    instances:
      aiChat:
        max-attempts: 3                               # (5)
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
```

| # | Parametro | Valore | Effetto |
|---|-----------|--------|---------|
| 1 | `sliding-window-size` | 10 | Circuit breaker guarda le ultime 10 chiamate |
| 2 | `failure-rate-threshold` | 50% | Se ≥5 chiamate su 10 falliscono, il circuito si apre |
| 3 | `wait-duration-in-open-state` | 30s | Resta aperto 30 secondi, poi passa a half-open |
| 4 | `permitted-number-of-calls-in-half-open-state` | 3 | In half-open lascia passare 3 richieste: se vanno tutte bene torna closed, se anche una fallisce torna open |
| 5 | `max-attempts` | 3 | Prima che il fallimento arrivi al circuit breaker, il retry fa 3 tentativi con backoff (1s → 2s → 4s) |

## Come si integrano Retry e Circuit Breaker

`AiClient.call()` ha due annotazioni:

```java
@Retry(name = "aiChat")
@CircuitBreaker(name = "aiChat", fallbackMethod = "fallback")
public String call(ChatClient.ChatClientRequestSpec spec) {
    ...
}
```

L'ordine di esecuzione è: **Retry → Circuit Breaker → Fallback**.

1. Arriva una richiesta
2. Viene eseguito `call()`. Se fallisce, **Retry** riprova fino a 3 volte (con backoff 1s, 2s, 4s)
3. Se anche dopo 3 tentativi fallisce, l'errore arriva al **Circuit Breaker**, che lo conta nella sliding window
4. Se la sliding window raggiunge ≥50% di errori, il circuito si **apre** per 30s
5. Mentre è aperto, le chiamate non arrivano nemmeno a `call()` — Resilience4j lancia subito `CallNotPermittedException`
6. Dopo 30s passa a **half-open**: lascia passare 3 chiamate reali
   - Se tutte vanno bene → **closed**
   - Se anche una fallisce → di nuovo **open**
7. Se il circuito è aperto o il retry esaurisce i tentativi, viene invocato il **fallback method**, che restituisce un messaggio user-friendly

## Il fallback

```java
public String fallback(ChatClient.ChatClientRequestSpec spec, Throwable t) {
    log.error("AI call failed after retries", t);
    return "The AI service is currently unavailable. Please try again later.";
}
```

Il fallback viene chiamato in due casi:
- **Dopo che Retry ha esaurito i 3 tentativi** — l'AI è tornata sempre errore
- **Circuito aperto** — `CallNotPermittedException` viene lanciata subito, senza chiamare l'AI

In entrambi i casi l'utente riceve un 200 con un messaggio amichevole, non un errore HTTP.

## Perché Retry + Circuit Breaker insieme?

| Pattern | Problema | Soluzione |
|---------|----------|-----------|
| **Retry** | Errore temporaneo (timeout, rete) | Riprova subito con backoff |
| **Circuit Breaker** | Errore persistente (AI down, rate limit) | Blocca le chiamate per non sprecare risorse e dare respiro al sistema |

Il retry gestisce i *malfunzionamenti transienti* (un timeout sporadico). Il circuit breaker gestisce i *guasti veri* (l'AI è giù da minuti). Insieme coprono entrambi gli scenari.
