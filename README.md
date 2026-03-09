# VE Mailer — Email Notification Broker

A full-stack application that lets users subscribe to email digest notifications for work items tracked in **Microfocus ALM Octane (ValueEdge)**. Subscribers choose a workspace, a pre-configured filter, and a delivery frequency (hourly, daily, or weekly). Subscriptions are protected by OTP-based email verification.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Data Model](#data-model)
- [API Reference](#api-reference)
- [Running Locally](#running-locally)
  - [Prerequisites](#prerequisites)
  - [Backend](#backend)
  - [Frontend](#frontend)
- [Configuration](#configuration)
  - [Backend — application.properties](#backend--applicationproperties)
  - [Backend — application-dev.properties](#backend--application-devproperties)
  - [Frontend — Environment Variables](#frontend--environment-variables)
- [Running Tests](#running-tests)
- [Building for Production](#building-for-production)
  - [Backend JAR](#backend-jar)
  - [Frontend Docker Image](#frontend-docker-image)
- [How It Works](#how-it-works)
  - [Subscription Flow](#subscription-flow)
  - [Filter Templates](#filter-templates)
  - [Notification Polling](#notification-polling)
  - [OTP Lifecycle](#otp-lifecycle)

---

## Overview

VE Mailer acts as a notification broker between Microfocus ALM Octane (ValueEdge) and end users. Instead of logging into ValueEdge to check on work items, users register their email and receive scheduled digests filtered to exactly what they care about.

Key capabilities:

- Browse registered **Workspaces** and their active subscriptions
- Subscribe, update, or unsubscribe via a simple **OTP-verified** flow
- Receive **email digests** at hourly, daily, or weekly cadence
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
| Frontend  | React 19, TypeScript, Vite 7, Tailwind CSS 4, Axios, react-hot-toast |
| Backend   | Java 17, Spring Boot 3.2.5                                          |
| Persistence | Spring Data JPA, H2 (dev), PostgreSQL (prod)                      |
| Security  | Spring Security, BCrypt OTP hashing                                 |
| Email     | Spring Mail (JavaMailSender)                                        |
| Scheduling | Spring `@Scheduled` — hourly / daily / weekly polling              |
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
│   │   │   ├── SecurityConfig.java   # BCrypt bean, CSRF disabled, all requests permitted
│   │   │   └── WebConfig.java        # CORS configuration
│   │   ├── controller/
│   │   │   ├── FilterController.java       # CRUD + execute filters
│   │   │   ├── SubscriptionController.java
│   │   │   └── WorkspaceController.java
│   │   ├── dto/
│   │   │   ├── FilterDto.java              # Create-filter request DTO
│   │   │   ├── SubscriptionRequestDto.java
│   │   │   ├── SubscriptionResponseDTO.java
│   │   │   ├── VerificationRequestDto.java
│   │   │   └── WorkspaceDto.java
│   │   ├── model/
│   │   │   ├── Workspace.java
│   │   │   ├── Filter.java                 # title, description, entityType, fields (JSON), criteria (JSON)
│   │   │   ├── FilterCriteriaClause.java   # POJO: field, operator, negate, values[]
│   │   │   ├── EmailSubscriber.java
│   │   │   ├── OtpRequest.java
│   │   │   ├── ActionType.java       # SUBSCRIBE | UPDATE | UNSUBSCRIBE
│   │   │   ├── Frequency.java        # HOURLY | DAILY | WEEKLY
│   │   │   └── Status.java           # PENDING | ACTIVE
│   │   ├── repository/
│   │   │   ├── EmailSubscriberRepository.java
│   │   │   ├── FilterRepository.java
│   │   │   ├── OtpRequestRepository.java
│   │   │   └── WorkspaceRepository.java
│   │   └── service/
│   │       ├── CleanupService.java   # Purges expired OTPs every 5 min
│   │       ├── EmailService.java     # Async OTP email sender
│   │       ├── FilterService.java    # Create filters + execute against Octane
│   │       ├── NotificationService.java # Async digest email sender
│   │       ├── OctaneCacheService.java  # In-memory Octane client cache
│   │       ├── OtpService.java       # OTP generation, hashing, validation
│   │       ├── PollingService.java   # Scheduled digest trigger
│   │       ├── SubscriptionService.java # Subscription business logic
│   │       └── ve/
│   │           ├── ValueEdgeProperties.java  # Typed config properties
│   │           └── VeUtils.java              # Octane client factory
│   └── src/main/resources/
│       ├── application.properties        # Base / shared config
│       └── application-dev.properties    # Dev profile overrides (ValueEdge creds)
│
└── frontend/                         # React + Vite application
    ├── src/
    │   ├── App.tsx                   # Root; view routing (landing / workspace / filters)
    │   ├── api.ts                    # Axios instance (reads VITE_BACKEND_ROOT_URL)
    │   ├── components/
    │   │   ├── LandingView.tsx       # Workspace picker + Filter Templates link
    │   │   ├── FilterBuilderView.tsx # Create / browse filter templates
    │   │   ├── OtpModal.tsx          # OTP entry modal
    │   │   └── WorkspaceDashboard.tsx # Subscription management + filter execution
    │   └── services/
    │       └── apiService.ts         # All backend API calls
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
  frequency       -- HOURLY | DAILY | WEEKLY
  status          -- PENDING | ACTIVE
  workspace_id    -- FK → Workspace
  filter_id       -- FK → Filter

OtpRequest
  id
  email
  actionType      -- SUBSCRIBE | UPDATE | UNSUBSCRIBE
  payload         -- JSON: { workspaceId, filterId, frequency }
  otpHash         -- BCrypt hash of the 6-digit OTP
  expiresAt       -- 10 minutes from creation
```

---

## API Reference

All endpoints are prefixed with `/api/v1`.

### Workspaces

| Method | Path                                   | Description                              |
|--------|----------------------------------------|------------------------------------------|
| `GET`  | `/workspaces`                          | List all registered workspaces           |
| `GET`  | `/workspaces/{workspaceId}/subscriptions` | List active subscriptions for a workspace |

### Filters

| Method | Path                                      | Body / Params                                          | Description                                    |
|--------|-------------------------------------------|--------------------------------------------------------|------------------------------------------------|
| `GET`  | `/filters`                                | —                                                      | List all saved filter templates                |
| `POST` | `/filters`                                | `{ title, description, entityType, fields[], criteria[] }` | Create a new filter template                   |
| `POST` | `/filters/{filterId}/execute?workspaceId=` | Query param: `workspaceId`                             | Execute a filter against a workspace's Octane instance and return matching entities |

**FilterCriteriaClause** (element of the `criteria` array):

```json
{
  "field": "defect_type",
  "operator": "EQUAL_TO",
  "negate": false,
  "values": ["Escaped"]
}
```

Supported operators: `EQUAL_TO`, `IN`.

### Subscriptions

| Method | Path                      | Body fields                                          | Description                         |
|--------|---------------------------|------------------------------------------------------|-------------------------------------|
| `POST` | `/subscriptions/request`  | `email`, `actionType`, `workspaceId`, `filterId`, `frequency` | Trigger OTP email for a subscription action |
| `POST` | `/subscriptions/verify`   | `email`, `otp`                                       | Verify OTP and execute the action   |

**`actionType`** values: `SUBSCRIBE`, `UPDATE`, `UNSUBSCRIBE`
**`frequency`** values: `HOURLY`, `DAILY`, `WEEKLY`

**Response codes:**
- `200 OK` — success
- `401 Unauthorized` — invalid or expired OTP
- `400 Bad Request` — validation failure or unexpected error

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

> **Without the `dev` profile** the `valueedge.*` properties will not be loaded and the filter execute endpoint will not be able to connect to Octane. All other endpoints work without it.

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
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Mail (points to local Mailpit on port 1025)
spring.mail.host=localhost
spring.mail.port=1025
spring.mail.username=test
spring.mail.password=test

spring.application.name=veemailer
```

To switch to PostgreSQL, add the following to `application-prod.properties` and run with `SPRING_PROFILES_ACTIVE=prod`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/notificationdb
spring.datasource.driverClassName=org.postgresql.Driver
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.h2.console.enabled=false
spring.jpa.show-sql=false
```

---

### Backend — `application-dev.properties`

Located at `backend/src/main/resources/application-dev.properties`. Active when the `dev` Spring profile is enabled. Contains credentials for the ValueEdge (Octane) integration.

```properties
valueedge.server-url=https://your-octane-server.example.com
valueedge.client-id=your-client-id
valueedge.client-secret=your-client-secret
valueedge.shared-space-id=4001
valueedge.workspace-id=5015
```

These values are bound to `ValueEdgeProperties` and injected into `FilterService`. **Do not commit real credentials to source control.**

---

### Frontend — Environment Variables

| Variable               | Description                           | Example                       |
|------------------------|---------------------------------------|-------------------------------|
| `VITE_BACKEND_ROOT_URL` | Base URL of the Spring Boot backend  | `http://localhost:8080`       |

Create a `.env.local` file in the `frontend/` directory with the variable above. Vite exposes only variables prefixed with `VITE_` to the browser bundle.

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
 │  frequency + email       │  POST /subscriptions/request
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

When a filter is **executed** (`POST /filters/{id}/execute?workspaceId=...`), the backend:

1. Loads the filter and workspace from the database
2. Retrieves the Octane server URL from `ValueEdgeProperties`
3. Obtains an authenticated `Octane` client via `OctaneCacheService`
4. Dynamically builds an Octane SDK `Query` from the criteria clauses
5. Fetches the specified entity type with the requested fields
6. Returns the results as a JSON array

The same dynamic query building is used by `PollingService` when sending scheduled digest emails.

### Notification Polling

`PollingService` runs on three schedules:

| Schedule | Trigger                |
|----------|------------------------|
| Hourly   | Every 3 600 000 ms     |
| Daily    | `cron: 0 0 0 * * ?`   |
| Weekly   | `cron: 0 0 0 * * MON` |

On each run it:

1. Fetches all `ACTIVE` subscribers matching the frequency
2. Groups them by `(workspaceId, filterId)` to avoid duplicate external API calls
3. Delegates to `FilterService.executeFilter()` once per group to query Octane
4. Passes results to `NotificationService`, which sends an async digest email to each subscriber in the group

### OTP Lifecycle

1. **Created** — `OtpService.createAndSendOtp()` generates a 6-digit OTP via `SecureRandom`, BCrypt-hashes it, and stores it with a 10-minute expiry. If an OTP record for the email already exists it is overwritten.
2. **Validated** — `OtpService.validateOtp()` checks existence, expiry, and BCrypt match.
3. **Consumed** — On successful verification the OTP record is immediately deleted.
4. **Swept** — `CleanupService` runs every 5 minutes and hard-deletes any OTP records whose `expiresAt` has passed, covering cases where the user never submitted their OTP.
