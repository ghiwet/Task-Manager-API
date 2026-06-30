# 📋 Task Manager API

A Spring Boot RESTful backend for managing tasks and users with OAuth2 authentication (Google, GitHub, Keycloak), Kafka event-driven notifications, task ownership, pagination, and Testcontainers-based testing. Flyway is used for database versioning, and Swagger provides interactive API documentation.

---

## 🚀 Features

- ✅ CRUD operations for tasks and users
- 🔐 OAuth2 authentication (Google, GitHub, Keycloak) + JWT resource server
- 👤 Task ownership — users only see and modify their own tasks
- 🗑️ Two-tier delete — users delete own tasks, admins delete any task within their tenant
- 📄 Paginated task listing with sorting
- 📨 Kafka event-driven notifications (CREATED, UPDATED, COMPLETED, DELETED)
- 💀 Dead letter topic (DLT) for failed message handling
- 🗃️ PostgreSQL database with Flyway migrations
- 📑 Swagger UI for API exploration
- 📊 Observability — Micrometer metrics, Prometheus scraping, Grafana dashboard
- 🔭 Distributed tracing — OpenTelemetry spans (HTTP, JDBC, Kafka) exported to Tempo, one trace per request across services
- 🚦 Rate limiting — per-user / per-IP token buckets (Bucket4j) with `429` + `Retry-After`
- 🛡️ Security hardening — HTTP security headers (HSTS, CSP, etc.) and request input validation
- 🏢 Multi-tenancy — tenant isolation enforced at the database layer with PostgreSQL row-level security (RLS)
- 🤖 AI task assistant — RAG over your tasks (Spring AI + pgvector), scoped to your tenant and tasks
- 🧪 Integration tests with MockMvc, EmbeddedKafka, and Testcontainers

---

## 🏗️ Tech Stack

- Java 25+
- Spring Boot 4.1
- Spring Framework 7
- Spring Security 7
- Spring Data JPA
- Flyway
- PostgreSQL
- Apache Kafka 4.3 (KRaft mode)
- Swagger (springdoc-openapi)
- Micrometer + Prometheus + Grafana
- OpenTelemetry (Micrometer Tracing) + Grafana Tempo (distributed tracing)
- Spring AI 2.0 (OpenAI chat, local ONNX embeddings, pgvector vector store)
- Bucket4j (rate limiting)
- Testcontainers
- Docker and Docker Compose (PostgreSQL, Keycloak, Kafka, kafka-ui, Prometheus, Grafana, Tempo)

---

## 📁 Project Structure
```
src
├── main
│ ├── java/com/example/taskmanager
│ │ ├── config # KafkaConfig, KafkaConsumerConfig, RateLimitProperties
│ │ ├── controller # TaskController, UserController
│ │ ├── event # TaskEvent, TaskEventKafkaPublisher, Consumers
│ │ ├── model # Task, AppUser (both tenant-scoped via tenant_id)
│ │ ├── repository # TaskRepository, AppUserRepository
│ │ ├── service # TaskService, AppUserService
│ │ ├── security # WebSecurityConfig, RateLimitFilter
│ │ ├── tenant # TenantContext, TenantFilter, Hibernate multi-tenancy config
│ │ └── ai # AssistantController/Service/Retriever, task embedding consumer
│ └── resources
│ ├── db/migration # Flyway SQL scripts
│ ├── application.properties
└── test
└── keycloak/rela-export.json
└── DockerFile
└── docker-compose.yml
└── pom.xml
```

## 🌱 Branch Info
 main:  OAuth2-secured API

 basic: Uses basic auth with JDBC users

 form: Form login with Thymeleaf-based login page
 
## 🔐 Registering OAuth2 Providers
To enable OAuth2 authentication using Google and GitHub, follow these steps to register your application on each provider.

### 📘 Register with GitHub

1. Go to [GitHub Developer Settings](https://github.com/settings/developers).
2. Click **New OAuth App**.
3. Fill in the form:

   - Application name: `Task Manager API`
   - Homepage URL: `http://localhost:8080`
   - Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`

4. After saving, GitHub will provide:` Client ID` and `Client Secret`
5. Add these to the application.properties

###  📗 Register with Google

1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Create a project or select an existing one.
3. Navigate to **APIs & Services → Credentials.**
4. Click **"Create Credentials" → OAuth client ID.**
5. Choose **Web application**.
6. Fill in:
   - Name: `Task Manager API`
   - Authorized JavaScript origins: ` http://localhost:8080`
   - Authorized redirect URIs: `http://localhost:8080/login/oauth2/code/google`
7. Click **Create** to get: `Client ID` and `Client Secret`
8. Add these to the `application.properties`
 ### 🔐 OAuth2 Setup
Keycloak works out of the box with `docker-compose up -d` (no extra config needed).

To enable GitHub and Google OAuth2, set the environment variables and activate the `oauth2` profile:
```bash
export GITHUB_CLIENT_ID=your-id
export GITHUB_CLIENT_SECRET=your-secret
export GOOGLE_CLIENT_ID=your-id
export GOOGLE_CLIENT_SECRET=your-secret

./mvnw spring-boot:run -Dspring-boot.run.profiles=oauth2
```
The GitHub/Google registration config lives in `application-oauth2.properties`.

## 🚦 Getting Started

### 🐳 Run Docker-Compose (PostgreSQL, Keycloak, Kafka, kafka-ui, Prometheus, Grafana, Tempo)

`docker-compose up -d`

###  ▶️ Run Application
`./mvnw spring-boot:run`

For the AI assistant's generation step, set an OpenAI key first (everything else runs without it):
```bash
export OPENAI_API_KEY=sk-...
```

### 🧪 Running Tests
`./mvnw test`

Tests use Testcontainers (PostgreSQL) and EmbeddedKafka — no external services needed.

### 📑 Swagger Documentation
`http://localhost:8080/swagger-ui/index.html`

### 📊 Kafka UI
`http://localhost:8083`

### 📈 Prometheus
`http://localhost:9090`

### 📉 Grafana
`http://localhost:3000` (admin/admin) — pre-provisioned dashboard with JVM, HTTP, task operations, Kafka events, and DB pool metrics. Traces are in **Explore → Tempo**.

### 🔭 Tempo (traces)
`http://localhost:3200` (queried through Grafana; spans are sent here via OTLP on `:4318`)

> Upgrading an existing stack? The Grafana datasources are provisioned with fixed UIDs. If you ran an
> earlier version, recreate the Grafana volume once (`docker compose down -v`, or remove the
> `grafana_data` volume) so the new UIDs provision cleanly.

## 🛡️ Security Hardening

### 🚦 Rate Limiting

Requests are throttled with [Bucket4j](https://bucket4j.com/) token buckets via a `RateLimitFilter` placed
just after JWT authentication in the security chain. Authenticated requests are keyed per JWT subject; anonymous
requests fall back to the client IP (`X-Forwarded-For` aware). Each response carries an `X-Rate-Limit-Remaining`
header; exceeded limits return `429 Too Many Requests` with a `Retry-After` header and an RFC 9457 `ProblemDetail`
body, and increment the `rate_limit_exceeded_total` Prometheus counter.

Limits are configurable in `application.properties`:

```properties
rate-limit.enabled=true
rate-limit.public-requests-per-minute=20          # keyed by IP
rate-limit.authenticated-requests-per-minute=60   # keyed by JWT subject
rate-limit.registration-requests-per-minute=5     # stricter, for /api/v1/users/register
rate-limit.bucket-cleanup-interval-minutes=10
```

### 🔒 Security Headers

Every response sets defensive HTTP headers via the Spring Security `headers()` DSL:

- `Strict-Transport-Security` (1 year, includeSubDomains, preload) — emitted over HTTPS
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Content-Security-Policy: default-src 'self'; ...`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: camera=(), microphone=(), geolocation=()`

### ✅ Input Validation

Registration binds a dedicated `UserRegistrationDto` (not the raw entity), preventing mass-assignment of `id`,
`version`, or `roles`. Constraints are enforced with Jakarta Bean Validation:

- **username** — 3–50 chars, letters/digits/`. _ -` only
- **password** — 8–128 chars, requiring upper- and lower-case letters, a digit, and a special character

Validation failures return `400 Bad Request` with a per-field `errors` map; duplicate usernames return `409 Conflict`.

## 🏢 Multi-Tenancy (Row-Level Security)

Tasks and users are scoped to a tenant via a `tenant_id` column, and isolation is enforced by **PostgreSQL
row-level security** — at the database layer, not just in application code. Even a query that forgets its
`WHERE` clause (or a future bug) physically cannot return another tenant's rows.

**How a request's tenant reaches the database:**

```
JWT  tenant_id claim
  → TenantFilter            (reads the claim, binds it to a request-scoped TenantContext)
  → CurrentTenantResolver   (Hibernate asks "which tenant?" per unit of work)
  → TenantConnectionProvider(runs set_config('app.current_tenant', …) on the pooled connection,
                             and resets it on release so nothing leaks across requests)
  → RLS policy              (USING / WITH CHECK: tenant_id = current_setting('app.current_tenant'))
```

The tenant comes from a custom `tenant_id` claim, added in Keycloak via a user attribute and a protocol
mapper (see `keycloak/realm-export.json`). `current_setting('app.current_tenant', true)` returns `NULL`
when unset, so a missing tenant context matches **no rows** (default deny) rather than erroring.

**Two database roles** make this airtight (RLS does not apply to a table's owner or a superuser):

| Role | Used by | Privilege | RLS |
|------|---------|-----------|-----|
| `app_rls` | the application at runtime | non-owner, DML only | **enforced** |
| `taskuser` | Flyway migrations | owner / superuser | bypassed (so migrations run) |

The app connects as the least-privilege `app_rls` role (`spring.datasource.*`), while Flyway connects as the
privileged owner (`spring.flyway.user`). The `app_rls` role, its grants, and the policies are created in the
`V5__enable_rls.sql` migration.

`TenantIsolationTest` connects the app as `app_rls` and proves isolation holds — including cases where the
same owner exists in two tenants, so only RLS (not the owner filter) can block cross-tenant access.

## 🔭 Distributed Tracing

Requests are traced with the **Micrometer Observation API** bridged to **OpenTelemetry**, exporting spans
over **OTLP/HTTP** to **Grafana Tempo**. A single request becomes one trace that follows the work across
every layer — and across the Kafka boundary:

```
HTTP POST /api/v1/tasks  (server span)
  ├── Spring Security  (filter chain, authn, authz)
  ├── JDBC             (connection, query, generated-keys)
  ├── task-events send     (Kafka producer span)
  └── task-events process  (Kafka consumer span — same trace via propagated context)
```

- **HTTP, RestClient, JDBC** spans are auto-instrumented; JDBC query spans come from
  [`datasource-micrometer`](https://github.com/jdbc-observations/datasource-micrometer).
- **Kafka propagation** is enabled by observation on the producer template and listener container
  (`spring.kafka.template.observation-enabled` / `spring.kafka.listener.observation-enabled`), so the
  consumer span joins the producer's trace via W3C `traceparent` headers.
- **Log correlation** — every log line includes `traceId`/`spanId`, and Grafana's Tempo datasource links
  spans back to Prometheus metrics for the same service.

Configurable in `application.properties`:

```properties
spring.application.name=task-manager-api
management.tracing.sampling.probability=1.0                                  # sample all in dev
management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:4318/v1/traces
```

**View traces:** Grafana → Explore → **Tempo** datasource → search (e.g. `{ name = "http post /api/v1/tasks" }`).
Metrics stay on the Prometheus pipeline; only traces go to Tempo.

> Note: tracing is wired via `spring-boot-starter-opentelemetry`. The starter also brings an OTLP metrics
> registry, so metrics are kept on the Prometheus pipeline with `management.otlp.metrics.export.enabled=false`.

## 🤖 AI Task Assistant (RAG)

Ask natural-language questions about your tasks. `POST /api/v1/assistant/query` runs a retrieval-augmented
generation flow that reuses the rest of the stack:

```
question
  → embed (local ONNX model, all-MiniLM)
  → similarity search in pgvector, scoped to your tenant (RLS) AND your tasks (owner filter)
  → OpenAI answers using only the retrieved tasks as context
  → { answer, retrievedTaskIds }
```

- **Personal scoping** — retrieval is bounded by **tenant** (RLS, the same `SET LOCAL app.current_tenant`
  boundary as the rest of the app) *and* **owner** (a metadata filter), so the assistant only ever sees
  what you can see through the task API. A colleague's tasks in the same tenant are never retrieved.
- **Event-driven indexing** — task embeddings are kept in sync off the Kafka task event stream (a
  dedicated consumer group upserts on create/update, removes on delete); no embedding API is used.
- **Cost controls** — the endpoint has its own stricter rate-limit bucket (`assistant-requests-per-minute`),
  separate from the general per-user limit.
- **Observability** — the whole flow is one trace (HTTP → `embedding` → `pg_vector query` → `chat`), and
  Spring AI exports Prometheus metrics: `gen_ai_client_operation_seconds` (chat/embedding latency, tagged by
  model/system) and `gen_ai_client_token_usage_total` (token cost, tagged `gen_ai_token_type=input|output|total`).
  Example token-cost query: `sum by (gen_ai_token_type) (rate(gen_ai_client_token_usage_total{gen_ai_system="openai"}[5m]))`.

Configure in `application.properties` (chat via OpenAI, embeddings local, store managed by Flyway `V6`):

```properties
spring.ai.model.chat=openai
spring.ai.model.embedding=transformers       # local ONNX; no embedding API
spring.ai.openai.api-key=${OPENAI_API_KEY:}
spring.ai.openai.chat.options.model=gpt-4o-mini
spring.ai.vectorstore.pgvector.initialize-schema=false   # the table + RLS come from Flyway
```

> Requires `OPENAI_API_KEY` for the generation step. Embeddings, retrieval, and tenant/owner scoping work
> without it; only the final answer needs the key.

## 🔗 API Endpoints
### 🔨 Task Endpoints
```
GET    /api/v1/tasks           — list own tasks (paginated: ?page=0&size=20)

POST   /api/v1/tasks           — create task (assigned to authenticated user)

GET    /api/v1/tasks/{id}      — get own task by ID

PUT    /api/v1/tasks/{id}      — update own task

DELETE /api/v1/tasks/{id}      — delete own task (ROLE_USER) or any task in the tenant (ROLE_ADMIN)
```

### 👤 User Endpoints
```
POST /api/v1/users/register — register user (validated username + password)

GET /api/v1/users/me — current user info (secured)
```

### 🤖 Assistant Endpoint
```
POST /api/v1/assistant/query — ask about your tasks (ROLE_USER); body {"question": "..."}
                            returns {"answer": "...", "retrievedTaskIds": [..]}
```
