# VE Mailer — Email Notification Broker

A full-stack application that lets users subscribe to email digest notifications for work items tracked in **Microfocus ALM Octane (ValueEdge)**. Subscribers choose a workspace, a pre-configured filter, and a custom notification schedule (daily or weekly, with one or more specific hours per day). Subscriptions are protected by OTP-based email verification.

---

## Table of Contents

- [VE Mailer — Email Notification Broker](#ve-mailer--email-notification-broker)
  - [Table of Contents](#table-of-contents)
  - [Overview](#overview)
  - [Architecture](#architecture)
  - [Tech Stack](#tech-stack)
  - [Project Structure](#project-structure)
  - [Data Model](#data-model)
  - [API Reference](#api-reference)
    - [Authentication (`/api/auth`)](#authentication-apiauth)
    - [Workspaces](#workspaces)
    - [Filters](#filters)
    - [Subscriptions](#subscriptions)
  - [Running Locally](#running-locally)
    - [Prerequisites](#prerequisites)
    - [Backend](#backend)
    - [Frontend](#frontend)
  - [Configuration](#configuration)
    - [Backend — `application.properties`](#backend--applicationproperties)
    - [Backend — `application-dev.properties`](#backend--application-devproperties)
    - [Frontend — Environment Variables](#frontend--environment-variables)
  - [Running Tests](#running-tests)
    - [Backend](#backend-1)
    - [Frontend](#frontend-1)
  - [Building for Production](#building-for-production)
    - [Backend JAR](#backend-jar)
    - [Frontend Docker Image](#frontend-docker-image)
  - [How It Works](#how-it-works)
    - [Subscription Flow](#subscription-flow)
    - [Filter Templates](#filter-templates)
      - [Filter Examples](#filter-examples)
    - [Notification Polling](#notification-polling)
    - [OTP Lifecycle](#otp-lifecycle)

---

## Overview

VE Mailer acts as a notification broker between Microfocus ALM Octane (ValueEdge) and end users. Instead of logging into ValueEdge to check on work items, users register their email and receive scheduled digests filtered to exactly what they care about.

Key capabilities:

- Browse registered **Workspaces** and their active subscriptions
- Subscribe, update, or unsubscribe via a simple **OTP-verified** flow
- Receive **email digests** on a custom schedule — daily or weekly (Mondays), at one or more specific hours you choose
- Create **Filter Templates** — structured query definitions (entity type, fields, criteria) that are stored as reusable templates and dynamically compiled into Octane SDK queries
- **Execute filters on demand** — preview matching results from ValueEdge directly in the UI before subscribing

---

## Architecture

```
┌─────────────────────┐        REST / JSON        ┌──────────────────────────┐
│   React Frontend    │ ◄────────────────────────► │  Spring Boot Backend     │
│   (Vite + TS)       │                            │  (Java 17, port 8080)    │
└─────────────────────┘                            └──────────┬───────────────┘
                                                              │
                                        ┌─────────────────────┼──────────────────┐
                                        │                     │                  │
                                   ┌────▼─────┐     ┌────────▼──────┐  ┌────────▼──────┐
                                   │ H2 / PG  │     │  SMTP Server  │  │  ValueEdge    │
                                   │ Database │     │  (Email)      │  │  (Octane API) │
                                   └──────────┘     └───────────────┘  └───────────────┘
```

The backend is stateless between requests. An in-memory cache (`OctaneCacheService`) keeps authenticated Octane sessions alive across polling cycles.

---

## Tech Stack

| Layer     | Technology                                                          |
|-----------|---------------------------------------------------------------------|
| Frontend  | React 19, TypeScript, Vite 7, Tailwind CSS 4, Axios, react-hot-toast, react-router-dom |
| Backend   | Java 17, Spring Boot 3.2.5                                          |
| Persistence | Spring Data JPA, H2 (dev), PostgreSQL (prod)                      |
| Security  | Spring Security, JWT (HMAC-SHA256), BCrypt password hashing, role-based access |
| Email     | Dynamic SMTP via DB-stored NotificationPreferences (DynamicMailSenderService) |
| AI        | Spring AI (OpenAI) — optional AI-generated ticket summaries; credentials stored in DB via AiPreferences (DynamicAiClientService) |
| Scheduling | Spring `@Scheduled` — cron-based hourly trigger dispatches to subscribers by schedule type and configured hours |
| Octane SDK | Microfocus ALM Octane SDK 25.4                                     |
| Build     | Maven (backend), npm (frontend)                                     |
| Containers | Nginx + Docker multi-stage (frontend)                              |

---

## Project Structure

```
ve-mailer/
├── backend/                          # Spring Boot application
│   ├── src/main/java/com/anushibinj/veemailer/
│   │   ├── NotificationBrokerApplication.java
│   │   ├── config/
│   │   │   ├── AppConfig.java        # Async + Scheduling enablement, RestTemplate bean
│   │   │   ├── Auth403AccessDeniedHandler.java # Returns HTTP 403 JSON for filter-level access denial
│   │   │   ├── Auth401EntryPoint.java # Returns HTTP 401 JSON for unauthenticated requests
│   │   │   ├── GlobalExceptionHandler.java # Centralized REST exception handling
│   │   │   ├── JwtAuthenticationFilter.java # JWT token validation filter
│   │   │   ├── SecurityConfig.java   # Spring Security: JWT stateless, role-based access, 401/403 auth failure handlers
│   │   │   └── WebConfig.java        # CORS configuration
│   │   ├── controller/
│   │   │   ├── AiPreferencesController.java        # Admin AI config (GET/PUT) — ADMIN only
│   │   │   ├── AuthController.java         # Authentication endpoints (signup, login, etc.)
│   │   │   ├── FilterController.java       # CRUD + execute filters
│   │   │   ├── NotificationPreferencesController.java # Admin SMTP config (GET/PUT)
│   │   │   ├── SubscriptionController.java
│   │   │   └── WorkspaceController.java
│   │   ├── dto/
│   │   │   ├── AiPreferencesResponseDto.java        # Masked AI config response (api key always masked)
│   │   │   ├── AiPreferencesUpdateDto.java          # AI config update request with URL validation
│   │   │   ├── ApiErrorResponse.java       # Structured error response
│   │   │   ├── ApiResponseWrapper.java     # Generic success/error wrapper
│   │   │   ├── AuthResponseDto.java        # JWT tokens + user profile
│   │   │   ├── ForgotPasswordRequestDto.java
│   │   │   ├── FilterDto.java              # Create-filter request DTO
│   │   │   ├── LoginRequestDto.java
│   │   │   ├── NotificationPreferencesResponseDto.java # Masked SMTP config response
│   │   │   ├── NotificationPreferencesUpdateDto.java   # SMTP config update request
│   │   │   ├── RefreshTokenRequestDto.java
│   │   │   ├── ResetPasswordDto.java
│   │   │   ├── ScheduleDto.java            # { type: DAILY|WEEKLY, hours: [int] }
│   │   │   ├── SignupRequestDto.java
│   │   │   ├── SubscriptionRequestDto.java
│   │   │   ├── SubscriptionResponseDTO.java
│   │   │   ├── VerificationRequestDto.java
│   │   │   ├── VerifyResetOtpDto.java
│   │   │   ├── VerifySignupOtpDto.java
│   │   │   └── WorkspaceDto.java
│   │   ├── model/
│   │   │   ├── AiPreferences.java            # AI provider config entity (apiKey, baseUrl, completionsPath, model)
│   │   │   ├── AppUser.java          # User entity (name, email, passwordHash, roles)
│   │   │   ├── Role.java             # Role entity (ADMIN, MEMBER)
│   │   │   ├── RefreshToken.java     # Refresh token entity (revocable, per-user)
│   │   │   ├── NotificationPreferences.java # SMTP config entity (host, port, username, password, TLS)
│   │   │   ├── Workspace.java
│   │   │   ├── Filter.java                 # title, description, entityType, fields (JSON), criteria (JSON)
│   │   │   ├── FilterCriteriaClause.java   # POJO: field, operator, negate, values[]
│   │   │   ├── EmailSubscriber.java
│   │   │   ├── OtpRequest.java
│   │   │   ├── ActionType.java       # SUBSCRIBE | UPDATE | UNSUBSCRIBE | SIGNUP_VERIFICATION | PASSWORD_RESET
│   │   │   ├── Frequency.java        # HOURLY | DAILY | WEEKLY (legacy, kept for migration)
│   │   │   ├── ScheduleType.java     # DAILY | WEEKLY
│   │   │   └── Status.java           # PENDING | ACTIVE
│   │   ├── repository/
│   │   │   ├── AiPreferencesRepository.java
│   │   │   ├── AppUserRepository.java
│   │   │   ├── EmailSubscriberRepository.java
│   │   │   ├── FilterRepository.java
│   │   │   ├── NotificationPreferencesRepository.java
│   │   │   ├── OtpRequestRepository.java
│   │   │   ├── RefreshTokenRepository.java
│   │   │   ├── RoleRepository.java
│   │   │   └── WorkspaceRepository.java
│   │   └── service/
│   │       ├── AdminBootstrapService.java # Seeds ADMIN user + roles on first boot
│   │       ├── AiPreferencesService.java  # Admin AI settings CRUD + API key masking
│   │       ├── AiSummaryService.java      # Generates AI ticket summaries via DynamicAiClientService
│   │       ├── AppUserDetailsService.java # Spring Security UserDetailsService
│   │       ├── AuthService.java      # Signup, login, forgot/reset password, token refresh
│   │       ├── CleanupService.java   # Purges expired OTPs every 5 min
│   │       ├── DynamicAiClientService.java  # Builds Spring AI ChatClient from DB config at runtime
│   │       ├── DynamicMailSenderService.java # Builds JavaMailSender from DB config
│   │       ├── EmailService.java     # Async OTP email sender
│   │       ├── FilterService.java    # Create filters + execute against Octane
│   │       ├── JwtService.java       # JWT token generation and validation
│   │       ├── NotificationPreferencesService.java # Admin SMTP settings CRUD
│   │       ├── NotificationService.java # Async digest email sender
│   │       ├── OctaneCacheService.java  # In-memory Octane client cache
│   │       ├── OtpService.java       # OTP generation, hashing, validation
│   │       ├── PollingService.java   # Hourly cron trigger — dispatches by schedule
│   │       ├── RefreshTokenService.java # Refresh token lifecycle + single-session enforcement
│   │       ├── ScheduleMigrationRunner.java # Startup migration: converts legacy Frequency records
│   │       ├── SubscriptionService.java # Subscription business logic
│   │       └── ve/
│   │           └── VeUtils.java              # Octane client factory
│   └── src/main/resources/
│       ├── application.properties        # Base / shared config
│       └── application-dev.properties    # Dev profile overrides (mail, auth, OpenAI)
│
└── frontend/                         # React + Vite application
    ├── src/
    │   ├── App.tsx                   # Root; React Router + AuthProvider
    │   ├── api.ts                    # Axios instance with JWT request interceptor + 403 session-expiry handler
    │   ├── components/
    │   │   ├── LandingView.tsx       # Workspace picker + Filter Templates link
    │   │   ├── FilterBuilderView.tsx # Create / browse filter templates
    │   │   ├── ProtectedRoute.tsx    # Auth guard with role-based access
    │   │   └── WorkspaceDashboard.tsx # Subscription management + filter execution
    │   ├── hooks/
    │   │   └── useAuth.tsx           # AuthContext + AuthProvider + useAuth hook
    │   ├── pages/
    │   │   ├── LoginPage.tsx         # Email + password login
    │   │   ├── SignupPage.tsx        # Registration with domain validation
    │   │   ├── VerifySignupPage.tsx  # OTP verification for new accounts
    │   │   ├── ForgotPasswordPage.tsx # Request password reset OTP
    │   │   ├── ResetPasswordPage.tsx # OTP verification + new password
    │   │   └── admin/
    │   │       ├── AdminControlPanel.tsx         # Left-sidebar admin dashboard
    │   │       ├── NotificationPreferencesPage.tsx # SMTP config form
    │   │       └── WorkspaceManagementPage.tsx   # Workspace CRUD
    │   ├── services/
    │   │   ├── apiService.ts         # All backend API calls (workspaces, filters, subscriptions)
    │   │   └── authService.ts        # Auth API calls + token management
    │   └── types/
    │       └── auth.ts               # TypeScript interfaces for auth DTOs
    └── Dockerfile                    # Multi-stage: Node build → Nginx serve
```

---

## Data Model

```
Workspace
  id (UUID PK)
  title
  sharedSpaceId   -- ValueEdge shared space
  workspaceId     -- ValueEdge workspace
  clientId        -- API client ID for this workspace
  clientKey       -- API client secret for this workspace

Filter
  id (UUID PK)
  title
  description
  entityType      -- Octane entity type (e.g. "defect", "story")
  fields          -- JSON array of field names to fetch (TEXT column)
  criteria        -- JSON array of FilterCriteriaClause objects (TEXT column)

  FilterCriteriaClause (embedded in criteria JSON):
    field         -- Octane field name
    operator      -- EQUAL_TO | IN
    negate        -- boolean (wraps clause in NOT)
    values[]      -- list of match values or Octane IDs

EmailSubscriber
  id (UUID PK)
  recipientEmail
  scheduleType    -- DAILY | WEEKLY
  frequency       -- HOURLY | DAILY | WEEKLY (legacy, nullable — kept for backward compat)
  status          -- PENDING | ACTIVE
  workspace_id    -- FK → Workspace
  filter_id       -- FK → Filter

subscriber_scheduled_hours  (element collection table)
  subscriber_id   -- FK → EmailSubscriber
  scheduled_hour  -- 0-23 (hour of day to notify)

OtpRequest
  id
  email
  actionType      -- SUBSCRIBE | UPDATE | UNSUBSCRIBE | SIGNUP_VERIFICATION | PASSWORD_RESET
  payload         -- JSON: { workspaceId, filterId, schedule: { type, hours[] } } or user signup data
  otpHash         -- BCrypt hash of the 6-digit OTP
  expiresAt       -- 10 minutes from creation

AppUser
  id (UUID PK)
  name
  email (UNIQUE)
  passwordHash    -- BCrypt hash
  enabled         -- boolean
  createdAt
  updatedAt

Role
  id (UUID PK)
  roleName (UNIQUE) -- ADMIN | MEMBER

user_roles (join table)
  user_id         -- FK → AppUser
  role_id         -- FK → Role

RefreshToken
  id (UUID PK)
  user_id         -- FK → AppUser
  token (UNIQUE)  -- UUID string
  expiresAt       -- 7 days from creation
  revoked         -- boolean

NotificationPreferences
  id (UUID PK)
  host            -- SMTP server host
  port            -- SMTP port
  username        -- SMTP username (also used as "from" address)
  password        -- SMTP password (masked in API responses)
  startTlsEnabled -- boolean
```

---

## API Reference

All endpoints are prefixed with `/api/v1` for business APIs, `/api/auth` for authentication, and `/api/admin` for admin configuration.

### Authentication (`/api/auth`)

| Method | Path                   | Body fields                                          | Auth Required | Description                             |
|--------|------------------------|------------------------------------------------------|:-------------:|-----------------------------------------|
| `POST` | `/auth/signup`         | `name`, `email`, `password`, `confirmPassword`       | No            | Register new account (sends OTP)        |
| `POST` | `/auth/verify-signup`  | `email`, `otp`                                       | No            | Verify OTP → create user → auto-login   |
| `POST` | `/auth/login`          | `email`, `password`                                  | No            | Login with credentials                  |
| `POST` | `/auth/refresh`        | `refreshToken`                                       | No            | Refresh access token                    |
| `POST` | `/auth/logout`         | `refreshToken`                                       | No            | Revoke refresh token                    |
| `POST` | `/auth/forgot-password`| `email`                                              | No            | Send password reset OTP                 |
| `POST` | `/auth/verify-reset-otp`| `email`, `otp`                                      | No            | Verify password reset OTP               |
| `POST` | `/auth/reset-password` | `email`, `otp`, `newPassword`, `confirmPassword`     | No            | Reset password (invalidates sessions)   |
| `GET`  | `/auth/me`             | —                                                    | Yes           | Get current user profile                |

**Signup restrictions:**
- Only allowed email domains can register (configurable via `app.auth.allowed-domains`)
- Password requirements: min 8 chars, uppercase, lowercase, digit, special character

**Token system:**
- Access token: JWT (15 min expiry, configurable)
- Refresh token: UUID (7 day expiry, configurable)
- Single-session enforcement: configurable via `app.auth.allow-multiple-sessions`

### Workspaces

All workspace endpoints require authentication. Mutation endpoints (POST/PUT/DELETE) require the `ADMIN` role.

| Method   | Path                      | Role required | Description                    |
|----------|---------------------------|:-------------:|--------------------------------|
| `GET`    | `/workspaces`             | Any           | List all registered workspaces |
| `GET`    | `/workspaces/{id}`        | Any           | Get workspace details          |
| `POST`   | `/workspaces`             | ADMIN         | Create a workspace             |
| `PUT`    | `/workspaces/{id}`        | ADMIN         | Update a workspace             |
| `DELETE` | `/workspaces/{id}`        | ADMIN         | Delete a workspace             |

### Filters

Filter template read endpoints are open to all authenticated users. Create/update require the `ADMIN` role.

| Method | Path                                           | Role required | Description                                     |
|--------|------------------------------------------------|:-------------:|-------------------------------------------------|
| `GET`  | `/workspaces/{id}/filters`                     | Any           | List filter templates for a workspace           |
| `POST` | `/workspaces/{id}/filters`                     | ADMIN         | Create a filter template                        |
| `PUT`  | `/workspaces/{id}/filters/{filterId}`          | ADMIN         | Update a filter template                        |
| `POST` | `/workspaces/{id}/filters/{filterId}/execute`  | Any           | Execute a filter against Octane and return entities |

**FilterCriteriaClause** (element of the `criteria` array):

```json
{
  "field": "defect_type",
  "operator": "IN",
  "values": ["Escaped"]
}
```

Supported operators: `IN`, `NOT_IN`.

### Subscriptions

All subscription endpoints require authentication. Users may only update/delete their own subscriptions (ownership enforced server-side). The on-demand `run` endpoint requires the `ADMIN` role.

**Subscription visibility:** `ADMIN` users see all subscriptions for the workspace; `MEMBER` users see only their own subscriptions. The frontend hides the "Recipient Email" column and labels the section "My Subscriptions" for `MEMBER` users.

| Method   | Path                                                      | Role required | Description                                              |
|----------|-----------------------------------------------------------|:-------------:|----------------------------------------------------------|
| `GET`    | `/workspaces/{id}/subscriptions`                          | Any           | ADMIN: all subscriptions; MEMBER: own subscriptions only |
| `POST`   | `/workspaces/{id}/subscriptions`                          | Any           | Subscribe to a filter template                           |
| `PUT`    | `/workspaces/{id}/subscriptions/{subId}`                  | Any (own)     | Update subscription schedule                             |
| `DELETE` | `/workspaces/{id}/subscriptions/{subId}`                  | Any (own)     | Unsubscribe                                              |
| `POST`   | `/workspaces/{id}/subscriptions/{subId}/run`              | ADMIN         | Immediately send a notification email                    |

### Admin — Notification Preferences (`/api/admin`)

All admin configuration endpoints require the `ADMIN` role. SMTP settings are stored in the database and used dynamically by the mail sender.

| Method | Path                                  | Role required | Description                          |
|--------|---------------------------------------|:-------------:|--------------------------------------|
| `GET`  | `/admin/notification-preferences`     | ADMIN         | Get SMTP config (password masked)    |
| `PUT`  | `/admin/notification-preferences`     | ADMIN         | Create/update SMTP config            |

**Password handling:** The `password` field in responses is always `"(unchanged)"`. On update, sending `"(unchanged)"` (or blank/null) preserves the existing password. A new value replaces it.

---

## Running Locally

### Prerequisites

| Tool | Minimum version |
|------|----------------|
| Java | 17             |
| Maven | 3.8+          |
| Node.js | 20+         |
| npm  | 9+             |

A local SMTP server is needed for OTP emails during development. [Mailpit](https://github.com/axllent/mailpit) is recommended:

```bash
# macOS (Homebrew)
brew install axllent/apps/mailpit && mailpit

# Docker
docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit
```

The web UI will be available at `http://localhost:8025`.

> After starting the backend, log in as admin and configure SMTP settings in the **Admin Control Panel → Configure Notification Preferences** (use `localhost` port `1025` for Mailpit).

---

### Backend

```bash
cd backend

# Run with the dev profile (loads application-dev.properties)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The API will start on **http://localhost:8080**.

The H2 console (for inspecting the database) is available at **http://localhost:8080/h2-console** with:

| Field    | Value                       |
|----------|-----------------------------|
| JDBC URL | `jdbc:h2:file:./data/notificationdb` |
| Username | `sa`                        |
| Password | `password`                  |

> The `dev` profile enables verbose security logging and configures dev-specific mail/auth settings. ValueEdge connection credentials (server URL, client ID, client key) are stored per-workspace in the database and managed through the Admin UI.

---

### Frontend

```bash
cd frontend

# Install dependencies
npm install

# Create a local env file
cp .env.example .env.local   # or create it manually (see below)

# Start the dev server
npm run dev
```

The app will be available at **http://localhost:5173**.

The `.env.local` file must contain:

```env
VITE_BACKEND_ROOT_URL=http://localhost:8080
```

---

## Configuration

### Backend — `application.properties`

Located at `backend/src/main/resources/application.properties`. Contains defaults that apply to all profiles.

```properties
# H2 (dev/test database)
spring.datasource.url=jdbc:h2:file:./data/notificationdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true

# Hibernate
spring.jpa.show-sql=true
spring.flyway.enabled=true

spring.application.name=veemailer

# Authentication
app.auth.allowed-domains=company.com,int-company.com
app.auth.allow-multiple-sessions=false
app.auth.jwt.secret=<your-256-bit-secret>
app.auth.jwt.access-token-expiration-ms=900000
app.auth.jwt.refresh-token-expiration-ms=604800000

# Admin Bootstrap (created on first startup)
app.bootstrap.admin.email=admin@company.com
app.bootstrap.admin.password=ChangeMeImmediately
app.bootstrap.admin.name=System Administrator
```

> **SMTP configuration** is no longer in properties files. Configure email settings via the **Admin Control Panel → Configure Notification Preferences** in the UI. Settings are stored in the `notification_preferences` database table.

> **AI configuration** is no longer in properties files. Configure AI provider settings via the **Admin Control Panel → Configure AI Preferences** in the UI. Settings are stored in the `ai_preferences` database table. The API key is never returned to the frontend.
To switch to PostgreSQL, add the following to `application-prod.properties` and run with `SPRING_PROFILES_ACTIVE=prod`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/notificationdb
spring.datasource.driverClassName=org.postgresql.Driver
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.h2.console.enabled=true
spring.jpa.show-sql=false
```

---

### Backend — `application-dev.properties`

Located at `backend/src/main/resources/application-dev.properties`. Active when the `dev` Spring profile is enabled. Contains development-specific overrides for mail, auth, logging, and AI settings.

> ValueEdge/Octane connection details (server URL, client ID, client key, shared space ID, workspace ID) are now stored **per workspace in the database** and managed via the Admin UI. No configuration file changes are needed when adding or changing Octane workspaces.

---

### AI Summary Configuration (Optional)

The AI Summary feature uses Spring AI with OpenAI to generate concise ticket summaries in email digests.

> **AI configuration is now database-backed.** No `spring.ai.openai.*` properties are required. Configure AI settings via the **Admin Control Panel → Configure AI Preferences** in the UI. Settings are stored in the `ai_preferences` database table.

Fields configurable through the Admin Control Panel:

| Field                  | Description                                          | Example                                   |
|------------------------|------------------------------------------------------|-------------------------------------------|
| API Key                | Provider API key — masked after save                 | `sk-...` or GitHub Copilot token          |
| Base URL               | AI provider base URL                                 | `https://api.openai.com`                  |
| Chat Completions Path  | Path for chat completion requests                    | `/v1/chat/completions` or `/chat/completions` |
| Model                  | Model identifier                                     | `gpt-4.1-mini`                            |

Prompts are stored in `backend/src/main/resources/prompts/` and can be customized without code changes:
- `ai-summary-system-prompt.md` — defines summarization behavior and tone
- `ai-summary-user-prompt.md` — template with placeholders for ticket data

---

### Frontend — Environment Variables

| Variable                | Description                                                              | Example                                                    |
|-------------------------|--------------------------------------------------------------------------|------------------------------------------------------------||
| `VITE_BACKEND_ROOT_URL` | Base URL of the Spring Boot backend                                      | `http://localhost:8080`                                    |
| `VITE_FOOTER_HTML`      | Optional HTML injected as the global app footer (sanitized before render) | `<div style="text-align:center">Powered by VE Mailer</div>` |

Create a `.env.local` file in the `frontend/` directory. Vite exposes only variables prefixed with `VITE_` to the browser bundle.

#### `VITE_FOOTER_HTML` — custom footer

Set this variable to any HTML snippet and it will be rendered as a `<footer>` element at the bottom of every page. Leave it empty (or unset) to hide the footer entirely.

```env
# Plain text footer
VITE_FOOTER_HTML=<div style="padding:8px;text-align:center;font-size:12px;">Powered by VE Mailer</div>

# Footer with a link
VITE_FOOTER_HTML=<div><a href="https://company.com">Company Portal</a> — Internal Use Only</div>
```

Allowed tags: `div`, `span`, `a`, `p`, `small`, `strong`, `em`, `br`, `ul`, `ol`, `li`.  
Blocked automatically: `<script>`, `<iframe>`, inline event handlers (`onclick`, `onerror`, …), and `javascript:` URLs.

---

## Running Tests

### Backend

```bash
cd backend

# Run all tests with coverage report
./mvnw verify

# Run tests only (skip coverage check)
./mvnw test
```

The JaCoCo coverage gate requires **≥ 90% instruction coverage**. Coverage reports are generated at:

```
backend/target/site/jacoco/index.html
```

### Frontend

The frontend does not currently have a test suite configured. Running `npm run lint` will check for ESLint issues:

```bash
cd frontend
npm run lint
```

---

## Building for Production

### Backend JAR

```bash
cd backend
./mvnw package -DskipTests

# The fat JAR will be at:
# backend/target/veemailer-0.0.1-SNAPSHOT.jar

# Run it with a specific profile
java -jar target/veemailer-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Frontend Docker Image

The frontend ships as a multi-stage Docker image: Node 20 builds the Vite bundle, then Nginx serves the static files on port 80.

```bash
cd frontend

# Build the image
docker build -t ve-mailer-frontend .

# Run it (set the backend URL at build time via build arg if needed,
# or configure Nginx to proxy /api to the backend)
docker run -p 80:80 ve-mailer-frontend
```

> **Note:** `VITE_BACKEND_ROOT_URL` is baked into the bundle at build time by Vite. To point the production image at the correct backend, either pass it as a build argument or use an Nginx proxy configuration to forward `/api` requests to the backend service.

---

## How It Works

### Subscription Flow

```
User                    Frontend               Backend
 │                          │                     │
 │  Select workspace        │                     │
 │ ─────────────────────►   │  GET /workspaces    │
 │                          │ ───────────────────► │
 │                          │ ◄─────────────────── │
 │                          │                     │
 │  Choose filter +         │                     │
 │  schedule + email        │  POST /subscriptions/request
 │ ─────────────────────►   │ ───────────────────► │
 │                          │                     │  Generate 6-digit OTP
 │                          │                     │  BCrypt hash → DB
 │  OTP arrives in email ◄──────────────────────────  Send email (async)
 │                          │                     │
 │  Enter OTP               │  POST /subscriptions/verify
 │ ─────────────────────►   │ ───────────────────► │
 │                          │                     │  Validate OTP
 │                          │                     │  Execute action (SUBSCRIBE/UPDATE/UNSUBSCRIBE)
 │                          │                     │  Delete OTP record
 │  Confirmed  ◄────────────│ ◄─────────────────── │
```

### Filter Templates

Filter templates replace the old hardcoded query approach. Instead of storing a raw Octane query string, each filter stores structured data:

1. **Entity type** — the Octane entity to query (e.g. `defect`, `story`)
2. **Fields** — which fields to return in the result set (e.g. `["id", "name", "phase", "owner"]`)
3. **Criteria** — an array of clauses that are AND-joined to build the Octane SDK query

#### Filter Examples

Here are some common filter configurations:

**Example 1: Defects Not Closed**
```
Entity Type: defect
Fields: id, name, phase, owner, creation_time
Criteria:
  - field: phase
    operator: NOT_IN
    values: ["phase.defect.closed"]
```

More examples to come.

When a filter is **executed** (`POST /filters/{id}/execute?workspaceId=...`), the backend:

1. Loads the filter and workspace from the database
2. Uses the workspace's `rootUrl` (stored in the database) to obtain an authenticated `Octane` client via `OctaneCacheService`
3. Obtains an authenticated `Octane` client via `OctaneCacheService`
4. Dynamically builds an Octane SDK `Query` from the criteria clauses
5. Fetches the specified entity type with the requested fields
6. Returns the results as a JSON array

The same dynamic query building is used by `PollingService` when sending scheduled digest emails.

### Notification Polling

`PollingService` runs on a single cron schedule that fires at the top of every hour (`0 0 * * * *`). Each run:

| Step | What happens |
|------|-------------|
| 1 | Determines current hour and day-of-week |
| 2 | Queries `DAILY` subscribers whose `scheduledHours` contains the current hour |
| 3 | Queries `WEEKLY` subscribers whose `scheduledHours` contains the current hour — **only on Mondays** |
| 4 | Groups matching subscribers by `(workspaceId, filterId)` to avoid duplicate API calls |
| 5 | Calls `FilterService.executeFilter()` once per group |
| 6 | Passes results to `NotificationService` to send async digest emails |

On startup, `ScheduleMigrationRunner` converts any legacy `Frequency`-based subscribers to the new `scheduleType` + `scheduledHours` model:

| Legacy `frequency` | Migrated to |
|--------------------|-------------|
| `HOURLY` | `DAILY` @ hours 0, 6, 12, 18 |
| `DAILY` | `DAILY` @ hour 8 |
| `WEEKLY` | `WEEKLY` @ hour 8 |

### OTP Lifecycle

1. **Created** — `OtpService.createAndSendOtp()` generates a 6-digit OTP via `SecureRandom`, BCrypt-hashes it, and stores it with a 10-minute expiry. If an OTP record for the email already exists it is overwritten.
2. **Validated** — `OtpService.validateOtp()` checks existence, expiry, and BCrypt match.
3. **Consumed** — On successful verification the OTP record is immediately deleted.
4. **Swept** — `CleanupService` runs every 5 minutes and hard-deletes any OTP records whose `expiresAt` has passed, covering cases where the user never submitted their OTP.
