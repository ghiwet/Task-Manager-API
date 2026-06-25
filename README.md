# 📋 Task Manager API

A Spring Boot RESTful backend for managing tasks and users with OAuth2 authentication (Google, GitHub, Keycloak), Kafka event-driven notifications, task ownership, pagination, and Testcontainers-based testing. Flyway is used for database versioning, and Swagger provides interactive API documentation.

---

## 🚀 Features

- ✅ CRUD operations for tasks and users
- 🔐 OAuth2 authentication (Google, GitHub, Keycloak) + JWT resource server
- 👤 Task ownership — users only see and modify their own tasks
- 🗑️ Two-tier delete — users delete own tasks, admins delete any task
- 📄 Paginated task listing with sorting
- 📨 Kafka event-driven notifications (CREATED, UPDATED, COMPLETED, DELETED)
- 💀 Dead letter topic (DLT) for failed message handling
- 🗃️ PostgreSQL database with Flyway migrations
- 📑 Swagger UI for API exploration
- 📊 Observability — Micrometer metrics, Prometheus scraping, Grafana dashboard
- 🚦 Rate limiting — per-user / per-IP token buckets (Bucket4j) with `429` + `Retry-After`
- 🛡️ Security hardening — HTTP security headers (HSTS, CSP, etc.) and request input validation
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
- Bucket4j (rate limiting)
- Testcontainers
- Docker and Docker Compose (PostgreSQL, Keycloak, Kafka, kafka-ui, Prometheus, Grafana)

---

## 📁 Project Structure
```
src
├── main
│ ├── java/com/example/taskmanager
│ │ ├── config # KafkaConfig, KafkaConsumerConfig, RateLimitProperties
│ │ ├── controller # TaskController, UserController
│ │ ├── event # TaskEvent, TaskEventKafkaPublisher, Consumers
│ │ ├── model # Task, AppUser
│ │ ├── repository # TaskRepository, AppUserRepository
│ │ ├── service # TaskService, AppUserService
│ │ └── security # WebSecurityConfig, RateLimitFilter
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

### 🐳 Run Docker-Compose (PostgreSQL, Keycloak, Kafka, kafka-ui, Prometheus, Grafana)

`docker-compose up -d`

###  ▶️ Run Application
`./mvnw spring-boot:run`

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
`http://localhost:3000` (admin/admin) — pre-provisioned dashboard with JVM, HTTP, task operations, Kafka events, and DB pool metrics

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
rate-limit.registration-requests-per-minute=5     # stricter, for /api/users/register
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

## 🔗 API Endpoints
### 🔨 Task Endpoints
```
GET    /api/tasks           — list own tasks (paginated: ?page=0&size=20)

POST   /api/tasks           — create task (assigned to authenticated user)

GET    /api/tasks/{id}      — get own task by ID

PUT    /api/tasks/{id}      — update own task

DELETE /api/tasks/{id}      — delete own task (ROLE_USER) or any task (ROLE_ADMIN)
```

### 👤 User Endpoints
```
POST /api/users/register — register user (validated username + password)

GET /api/users/me — current user info (secured)
```
