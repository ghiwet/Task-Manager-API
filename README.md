# 📋 Task Manager API

A Spring Boot RESTful backend for managing tasks and users with support for multiple authentication strategies: Basic Auth, Form-based Login, and OAuth2 (Google, GitHub, Keycloak). Flyway is used for database versioning, and Swagger provides interactive API documentation.

---

## 🚀 Features

- ✅ CRUD operations for tasks and users
- 🔐 Multiple authentication modes:
  - Basic Auth (with in-memory/JDBC users)
  - Form-based login
  - OAuth2 login (Google, GitHub, Keycloak)
- 🗃️ PostgreSQL database with Flyway migrations
- 📑 Swagger UI for API exploration
- 🧪 Unit and integration tests with MockMvc

---

## 🏗️ Tech Stack

- Java 25+
- Spring Boot 4.1
- Spring Framework 7
- Spring Security 7
- Spring Data JPA
- Flyway
- PostgreSQL
- Swagger (springdoc-openapi)
- Docker and Docker Compose (for PostgreSQL and Keycloak)

---

## 📁 Project Structure
```
src
├── main
│ ├── java/com/example/taskmanager
│ │ ├── controller # TaskController, UserController
│ │ ├── model # Task, AppUser
│ │ ├── repository # TaskRepository, UserRepository
│ │ ├── service # TaskService, AppUserService, OAuth2UserService
│ │ └── security # Security configuration
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

### 🐳 Run Docker-Compose (for DB and Keycloak)

`docker-compose up -d`

###  ▶️ Run Application
`./mvnw spring-boot:run`

### 🧪 Running Tests
`./mvnw test`

Tests are located in src/test/, covering services and controllers with MockMvc and JUnit.

### 📑 Swagger Documentation
`http://localhost:8080/swagger-ui/index.html`

## 🔗 API Endpoints
### 🔨 Task Endpoints
```
GET /api/tasks — list all tasks

POST /api/tasks — create task

GET /api/tasks/{id} — get task by ID

PUT /api/tasks/{id} — update task

DELETE /api/tasks/{id} — delete task
```

### 👤 User Endpoints
```
POST /api/users/register — register user

GET /api/users/me — current user info (secured)
```
