# Resilient Proxy

![CI](https://github.com/dstarstaff777-bit/resilient-proxy/actions/workflows/ci.yml/badge.svg)

Proxy-сервис на Spring Boot, демонстрирующий паттерны устойчивости (Resilience4j) на двух независимых симулированных downstream-зависимостях, с live-дашбордом состояния Circuit Breaker в реальном времени через SSE.

## Что здесь показано

- **Circuit Breaker** — полный жизненный цикл состояний (`CLOSED → OPEN → HALF_OPEN → CLOSED`) на двух независимых instance'ах (`orders`, `notifications`) с разными порогами по критичности downstream
- **Retry** — с подтверждённым (через анализ стектрейса, не по документации на слово) реальным порядком выполнения относительно Circuit Breaker: `Retry(CircuitBreaker(RateLimiter(Bulkhead(...))))` — каждая попытка retry засчитывается брейкером как отдельный вызов
- **Bulkhead** и **RateLimiter** — ограничение параллельных вызовов и троттлинг, протестированы изолированно от Spring-контекста
- **Изоляция failure domain** — деградация одного downstream не затрагивает состояние брейкера другого (покрыто тестом)
- **Таймаут на уровне HTTP-клиента** — осознанно не через `slowCallDurationThreshold` (это лишь метрика) и не через `TimeLimiter`, а явным `.timeout()` на `WebClient`
- **Live-дашборд** на SSE (`/monitoring/events`) — переходы состояния брейкера видны в браузере в реальном времени, без polling
- **Actuator** — `/actuator/circuitbreakers`, `/actuator/circuitbreakerevents`; health indicator осознанно завязан на состояние брейкеров (с пониманием, что в проде это потребовало бы разделения readiness/liveness)
- **Correlation ID** через MDC — все попытки одного логического запроса (включая повторы Retry) помечены одним `requestId` в логах

## Архитектура

```
POST /downstream/{name}/chaos?failureRate=80   (управление нестабильностью на лету)
                    |
                    v
[Browser: SSE dashboard] <== SSE ==  [CircuitBreakerEventBridge]
                                              ^
                                              | state transition events
GET /proxy/{name} -> [ProxyController] -> [ProxyService] --Retry/CircuitBreaker/RateLimiter/Bulkhead--> [DownstreamClient] --> [FlakyDownstreamController]
```

## Запуск

```bash
./gradlew bootRun
```

Откроется на `http://localhost:8080` — там же живёт дашборд.

## Тесты

```bash
./gradlew test
```

Покрыто: открытие/закрытие брейкера, отказ без похода к downstream при `OPEN`, изоляция между `orders` и `notifications`, переход через `HALF_OPEN`, Bulkhead и RateLimiter (изолированно, через чистый Resilience4j API).

## Ключевые эндпоинты

| Метод | Путь                          | Назначение                                  |
|-------|-------------------------------|----------------------------------------------|
| GET   | `/proxy/orders`                | Резильентный вызов downstream `orders`       |
| GET   | `/proxy/notifications`         | Резильентный вызов downstream `notifications`|
| POST  | `/downstream/{name}/chaos`     | Управление процентом отказов на лету         |
| GET   | `/monitoring/events`           | SSE-поток переходов состояния брейкеров      |
| GET   | `/actuator/circuitbreakers`    | Текущее состояние всех брейкеров             |
| GET   | `/actuator/circuitbreakerevents` | История переходов                          |

## Стек

Spring Boot 3.3.4, Resilience4j 2.2.0, Spring WebFlux (только ради `WebClient`/SSE — MVC остаётся основным стеком), WireMock (тесты), Gradle (Kotlin DSL), Java 21.