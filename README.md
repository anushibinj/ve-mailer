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
    - [Public Issue Reporting](#public-issue-reporting)
    - [Admin — Issues Dashboard (`/api/admin/issues`)](#admin--issues-dashboard-apiadminissues)
    - [Admin — Notification Preferences (`/api/admin`)](#admin--notification-preferences-apiadmin)
    - [Admin — Mail Analytics (`/api/admin/mail-analytics`)](#admin--mail-analytics-apiadminmail-analytics)
  - [Running Locally](#running-locally)
    - [Prerequisites](#prerequisites)
    - [Backend](#backend)
    - [Frontend](#frontend)
  - [Configuration](#configuration)
    - [Backend — `application.properties`](#backend--applicationproperties)
    - [Backend — `application-dev.properties`](#backend--application-devproperties)
    - [AI Summary Configuration (Optional)](#ai-summary-configuration-optional)
    - [Triage SLA Custom Field](#triage-sla-custom-field)
    - [Frontend — Environment Variables](#frontend--environment-variables)
      - [`VITE_ALLOW_CUSTOM_QUERY_STRING` — query-string filter workflow toggle](#vite_allow_custom_query_string--query-string-filter-workflow-toggle)
      - [`VITE_FOOTER_HTML` — custom footer](#vite_footer_html--custom-footer)
    - [Docker — Timezone (`TZ`)](#docker--timezone-tz)
  - [Running Tests](#running-tests)
    - [Backend](#backend-1)
    - [Frontend](#frontend-1)
  - [Building for Production](#building-for-production)
    - [Backend JAR](#backend-jar)
    - [Frontend Docker Image](#frontend-docker-image)
  - [Frontend URL Structure](#frontend-url-structure)
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
- Switch the workspace dashboard between **grid** and **list** views (persisted in browser localStorage)
- Lazy-load page-level and large feature components with React `lazy()` + `Suspense` for route/tab-level code splitting
- Subscribe, update, or unsubscribe via a simple **OTP-verified** flow
- Receive **email digests** on a custom schedule — daily or weekly (Mondays), at one or more specific hours you choose
- Create **Filter Templates** — structured query definitions (entity type, fields, criteria) that are stored as reusable templates and dynamically compiled into Octane SDK queries
- **Execute filters on demand** — preview matching results from ValueEdge directly in the UI before subscribing
- Add **custom pseudo-fields** such as **✨ AI Summary** and **Triage SLA** to enrich preview/email output without changing Octane metadata
- Raise public **Issue Reports** from the global footer and triage them in an admin-only dashboard

---

## Architecture

```
┌─────────────────────┐        REST / JSON         ┌──────────────────────────┐
│   React Frontend    │ ◄────────────────────────► │  Spring Boot Backend     │
│   (Vite + TS)       │                            │  (Java 17, port 8080)    │
└─────────────────────┘                            └──────────┬───────────────┘
                                                              │
                                        ┌─────────────────────┼──────────────────┐
                                        │                     │                  │
                                   ┌────▼─────┐      ┌────────▼──────┐  ┌────────▼──────┐
                                   │ H2 / PG  │      │  SMTP Server  │  │  ValueEdge    │
                                   │ Database │      │  (Email)      │  │  (Octane API) │
                                   └──────────┘      └───────────────┘  └───────────────┘
```

The backend is stateless between requests. An in-memory cache (`OctaneCacheService`) keeps authenticated Octane sessions alive across polling cycles.  
All Octane SDK requests include header `hpeclienttype=HPE_MQM_UI` to simulate Octane frontend behavior.

---

## Tech Stack

| Layer     | Technology                                                          |
|-----------|---------------------------------------------------------------------|
| Frontend  | React 19, TypeScript, Vite 7, Tailwind CSS 4, Axios, react-hot-toast, react-router-dom, Recharts |
| Backend   | Java 17, Spring Boot 3.2.5                                          |
| Persistence | Spring Data JPA, H2 (dev), PostgreSQL (prod)                      |
| Security  | Spring Security, JWT (HMAC-SHA256), BCrypt password hashing, role-based access (ADMIN, WORKSPACE_ADMIN, MEMBER) |
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
│   │   │   ├── FilterController.java       # CRUD + execute + clone + delete filters
│   │   │   ├── GeneralSettingsController.java      # Admin general settings (GET/PUT) — ADMIN only
│   │   │   ├── MailAnalyticsController.java        # Admin mail analytics (summary, charts, history) — ADMIN only
│   │   │   ├── NotificationPreferencesController.java # Admin SMTP config (GET/PUT)
│   │   │   ├── OctaneMetadataController.java # Easy Filter Builder metadata (GET fields, GET field-values)
│   │   │   ├── RecipientGroupController.java       # Recipient group CRUD + member management (workspace-scoped)
│   │   │   ├── SubscriptionController.java
│   │   │   ├── UserManagementController.java       # Admin user listing — ADMIN only
│   │   │   └── WorkspaceController.java
│   │   ├── dto/
│   │   │   ├── AiPreferencesResponseDto.java        # Masked AI config response (api key always masked)
│   │   │   ├── AiPreferencesUpdateDto.java          # AI config update request with URL validation
│   │   │   ├── ApiErrorResponse.java       # Structured error response
│   │   │   ├── ApiResponseWrapper.java     # Generic success/error wrapper
│   │   │   ├── AuthResponseDto.java        # JWT tokens + user profile
│   │   │   ├── ForgotPasswordRequestDto.java
│   │   │   ├── FilterDto.java              # Create-filter request DTO
│   │   │   ├── GeneralSettingsDto.java     # Query limit setting DTO
│   │   │   ├── LoginRequestDto.java
│   │   │   ├── NotificationPreferencesResponseDto.java # Masked SMTP config response
│   │   │   ├── NotificationPreferencesUpdateDto.java   # SMTP config update request
│   │   │   ├── RefreshTokenRequestDto.java
│   │   │   ├── ResetPasswordDto.java
│   │   │   ├── UserSummaryDto.java         # User listing DTO (id, name, email, roles, subscribedFilterCount)
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
│   │   │   ├── RecipientGroup.java           # Recipient group entity (name, description, memberEmails)
│   │   │   ├── Role.java             # Role entity (ADMIN, MEMBER, WORKSPACE_ADMIN)
│   │   │   ├── RefreshToken.java     # Refresh token entity (revocable, per-user)
│   │   │   ├── NotificationPreferences.java # SMTP config entity (host, port, username, password, TLS)
│   │   │   ├── DeliveryStatus.java          # Enum: SUCCESS | FAILED | SKIPPED
│   │   │   ├── MailAuditLog.java            # Mail delivery audit record entity
│   │   │   ├── Workspace.java
│   │   │   ├── WorkspaceAdminMapping.java   # Maps users to workspaces they administer
│   │   │   ├── Filter.java                 # title, description, entityType, fields (JSON), criteria (JSON), orderBy, orderByDirection
│   │   │   ├── FilterCriteriaClause.java   # POJO: field, operator, values[], logicalOperator (AND|OR)
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
│   │   │   ├── MailAuditLogRepository.java          # Analytics aggregation queries + paginated history
│   │   │   ├── NotificationPreferencesRepository.java
│   │   │   ├── OtpRequestRepository.java
│   │   │   ├── RefreshTokenRepository.java
│   │   │   ├── RecipientGroupRepository.java
│   │   │   ├── RoleRepository.java
│   │   │   ├── WorkspaceAdminRepository.java
│   │   │   └── WorkspaceRepository.java
│   │   └── service/
│   │       ├── AdminBootstrapService.java # Seeds ADMIN user + roles on first boot
│   │       ├── AiPreferencesService.java  # Admin AI settings CRUD + API key masking
│   │       ├── AiSummaryService.java      # Generates AI ticket summaries via DynamicAiClientService
│   │       ├── AppUserDetailsService.java # Spring Security UserDetailsService
│   │       ├── AuthService.java      # Signup, login, forgot/reset password, token refresh
│   │       ├── CleanupService.java   # Purges expired OTPs and invite magic links every 5 min
│   │       ├── DynamicAiClientService.java  # Builds Spring AI ChatClient from DB config at runtime
│   │       ├── DynamicMailSenderService.java # Builds JavaMailSender from DB config
│   │       ├── EmailService.java     # Async OTP + invite magic-link email sender
│   │       ├── FilterService.java    # CRUD + clone + execute (conditional limit) against Octane
│   │       ├── GeneralSettingsService.java  # DB-first query limit with property fallback
│   │       ├── JwtService.java       # JWT token generation and validation
│   │       ├── MailAnalyticsService.java     # Analytics aggregation + paginated history
│   │       ├── MailAuditService.java         # Async audit logging for mail dispatches
│   │       ├── NotificationPreferencesService.java # Admin SMTP settings CRUD
│   │       ├── NotificationService.java # Async digest email sender
│   │       ├── OctaneCacheService.java  # In-memory Octane client cache
│   │       ├── InviteMagicLinkService.java # Single-use invite token generation + validation
│   │       ├── OtpService.java       # OTP generation, hashing, validation (signup/reset/subscriptions)
│   │       ├── PollingService.java   # Hourly cron trigger — dispatches by schedule
│   │       ├── RecipientGroupService.java # Recipient group CRUD + member management
│   │       ├── RefreshTokenService.java # Refresh token lifecycle + single-session enforcement
│   │       ├── ScheduleMigrationRunner.java # Startup migration: converts legacy Frequency records
│   │       ├── SubscriptionService.java # Subscription business logic
│   │       ├── WorkspaceAdminService.java # Workspace admin permission checks and CRUD
│   │       └── ve/
│   │           └── VeUtils.java              # Octane client factory
│   └── src/main/resources/
│       ├── application.properties        # Base / shared config
│       └── application-dev.properties    # Dev profile overrides (mail, auth, OpenAI)
│
└── frontend/                         # React + Vite application
    ├── src/
    │   ├── App.tsx                   # Root; URL-based React Router, ThemeToggle, AppShell, WorkspaceShell
    │   ├── api.ts                    # Axios instance with JWT request interceptor + 403 session-expiry handler
    │   ├── components/
    │   │   ├── ui/                   # Shared design-system components (Button, Badge, Card, Input, Skeleton, etc.)
    │   │   ├── LandingView.tsx       # Workspace picker (grid/list toggle persisted per browser) + Filter Templates link
    │   │   ├── FilterBuilderView.tsx # Create / browse filter templates; global_id_udf cells are hyperlinked
    │   │   ├── ProtectedRoute.tsx    # Auth guard with role-based access
    │   │   ├── LoadingPlaceholder.tsx # Shared Suspense fallback (spinner + contextual loading text)
    │   │   ├── RecipientGroupsView.tsx # Workspace-scoped recipient group management; accordion shows read-only member badges
    │   │   └── WorkspaceDashboard.tsx # Subscription management + filter execution with sortable/filterable table
    │   ├── hooks/
    │   │   └── useAuth.tsx           # AuthContext + AuthProvider + useAuth hook
    │   ├── pages/
    │   │   ├── LoginPage.tsx         # Email + password login
    │   │   ├── SignupPage.tsx        # Registration with domain validation
    │   │   ├── VerifySignupPage.tsx  # OTP verification for new accounts
    │   │   ├── ForgotPasswordPage.tsx # Request password reset OTP
    │   │   ├── ResetPasswordPage.tsx # OTP verification + new password
    │   │   └── admin/
    │   │       ├── AdminControlPanel.tsx         # Left-sidebar admin dashboard; tab routing via ?tab= query param
    │   │       ├── AiPreferencesPage.tsx          # AI model config form
    │   │       ├── GeneralSettingsPage.tsx        # Query result limit config (supports -1 for unlimited)
    │   │       ├── MailAnalyticsPage.tsx          # Mail delivery analytics dashboard (charts + history)
    │   │       ├── NotificationPreferencesPage.tsx # SMTP config form
    │   │       ├── RecipientGroupsPage.tsx        # Recipient group management with workspace selector
    │   │       ├── UsersPage.tsx                  # All registered users with sortable columns + role badges
    │   │       ├── WorkspaceAdminManager.tsx      # Assign/remove workspace admins per workspace (available to ADMIN and WORKSPACE_ADMIN)
    │   │       └── WorkspaceManagementPage.tsx    # Workspace CRUD
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
  workspaceShortcode -- Business shortcode for the workspace (e.g. "77BD")
  sharedSpaceId   -- ValueEdge shared space
  workspaceId     -- ValueEdge workspace
  clientId        -- API client ID for this workspace
  clientKey       -- API client secret for this workspace
  status          -- ENABLED | DRAFT | DISABLED (lifecycle state)

Filter
  id (UUID PK)
  title
  description
  entityType      -- Octane filter scope (e.g. "backlog_items", "epic", "feature")
  fields          -- JSON array of field names to fetch (TEXT column)
  criteria        -- JSON array of FilterCriteriaClause objects (TEXT column)
  orderBy         -- optional field used for query sorting
  orderByDirection -- optional sort direction: ASC (default) | DESC

  FilterCriteriaClause (embedded in criteria JSON):
    field         -- Octane field name
    operator      -- type-driven operator (IN/NOT_IN, EQ/NEQ, GT/GTE/LT/LTE, CONTAINS/NOT_CONTAINS/STARTS_WITH, IS_EMPTY/IS_NOT_EMPTY)
    values[]      -- list of match values or Octane IDs
    logicalOperator -- AND | OR (join with previous clause)
    referenceValues -- nullable boolean; true = query as field EQ {id IN ...}

EmailSubscriber
  id (UUID PK)
  recipientEmail
  scheduleType    -- DAILY | WEEKLY
  triageSlaThreshold -- GREEN | YELLOW | RED (used when the filter includes Triage SLA)
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

InviteMagicLink
  id
  email (UNIQUE)
  tokenHash       -- SHA-256 hash of a high-entropy random token
  expiresAt       -- time-limited invite link expiry
  usedAt          -- null until first successful use (single-use enforcement)
  resendCount     -- cooldown multiplier for repeated link requests
  lastSentAt      -- timestamp of most recent send

AppUser
  id (UUID PK)
  name
  email (UNIQUE)
  passwordHash    -- BCrypt hash
  enabled         -- boolean
  mustSetPassword -- true when admin-created; cleared when user accepts invite
  createdAt
  updatedAt

Role
  id (UUID PK)
  roleName (UNIQUE) -- ADMIN | MEMBER | WORKSPACE_ADMIN

user_roles (join table)

WorkspaceAdminMapping
  id (UUID PK)
  workspace_id    -- FK → Workspace
  user_id         -- FK → AppUser
  createdAt
  createdBy
  UNIQUE(workspace_id, user_id)
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

MailAuditLog
  id (UUID PK)
  workspaceId         -- nullable, workspace that triggered the mail
  workspaceTitle      -- denormalized workspace name
  recipientEmail      -- recipient address
  filterTemplateId    -- nullable, filter template used
  filterTitle         -- denormalized filter name
  subscriptionId      -- nullable, originating subscription
  userId              -- nullable, user who owns the subscription
  mailSubject         -- email subject line
  ticketCount         -- number of tickets in digest
  deliveryStatus      -- SUCCESS | FAILED | SKIPPED
  failureReason       -- nullable, error message on failure (max 2000 chars)
  sentAt              -- timestamp of dispatch (indexed)
  durationMs          -- time to send in milliseconds

IssueReport
  id (UUID PK)
  reporterEmail       -- optional email supplied by the reporter
  message             -- optional issue description
  screenshotData      -- optional screenshot bytes (PostgreSQL BYTEA)
  screenshotContentType -- optional MIME type (e.g. image/png)
  screenshotFileName  -- optional uploaded filename
  status              -- OPEN | IN_PROGRESS | RESOLVED | CLOSED
  createdAt
  updatedAt

RecipientGroup
  id (UUID PK)
  workspace_id        -- FK → Workspace
  name                -- group display name (UNIQUE per workspace)
  description         -- optional description
  createdAt
  createdBy           -- email of the admin who created the group

recipient_group_members (element collection)
  group_id            -- FK → RecipientGroup
  member_email        -- email address of the group member
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
| `POST` | `/auth/request-invite-link` | `email`                                        | No            | Request invite magic link for pending account |
| `GET`  | `/auth/verify-invite-link`  | Query: `token`                                  | No            | Validate invite magic link status       |
| `POST` | `/auth/accept-invite`       | `token`, `newPassword`, `confirmPassword`       | No            | Accept admin invite link — sets password, auto-logs in |
| `GET`  | `/auth/me`             | —                                                    | Yes           | Get current user profile                |

**Signup restrictions:**
- Only allowed email domains can register (configurable via `app.auth.allowed-domains`)
- Password requirements: min 8 chars, uppercase, lowercase, digit, special character

**Token system:**
- Access token: JWT (15 min expiry, configurable)
- Refresh token: UUID (7 day expiry, configurable)
- Single-session enforcement: configurable via `app.auth.allow-multiple-sessions`

### Workspaces

All workspace endpoints require authentication. `DELETE` requires global `ADMIN` only; `POST` and `PUT` are allowed for both `ADMIN` and `WORKSPACE_ADMIN` (with workspace-admin field restrictions enforced in backend service).

| Method   | Path                      | Role required | Description                                                        |
|----------|---------------------------|:-------------:|--------------------------------------------------------------------|
| `GET`    | `/workspaces`             | Any           | List workspaces (role-aware: normal users see ENABLED only, admins see ENABLED+DRAFT) |
| `GET`    | `/workspaces/all`         | ADMIN         | List ALL workspaces including DISABLED (management view)           |
| `GET`    | `/workspaces/{id}`        | Any           | Get workspace details                                              |
| `POST`   | `/workspaces`             | ADMIN / WORKSPACE_ADMIN | Create a workspace (defaults to DRAFT status). `WORKSPACE_ADMIN` users may create an unlimited number of workspaces and are **automatically assigned as workspace admin** of the workspace they just created (no additional action required) |
| `PUT`    | `/workspaces/{id}`        | ADMIN / WORKSPACE_ADMIN | Update a workspace (`WORKSPACE_ADMIN` restricted to workspaces they administer; can update connection fields, workspace shortcode, and `status` (visibility); cannot change `title`) |
| `DELETE` | `/workspaces/{id}`        | ADMIN (Super Admin only) | Delete a workspace. `WORKSPACE_ADMIN` users can never delete a workspace, even one they created or administer — enforced on both backend (`@PreAuthorize("hasRole('ADMIN')")`) and frontend (Delete action hidden/disabled for non-`ADMIN` users) |
| `POST`   | `/workspaces/test-connection` | ADMIN / WORKSPACE_ADMIN | Validate workspace connectivity via Octane SDK by reading `stories` with `limit=1`; returns success-with-warning when connection works but no data is returned |
| `GET`    | `/workspaces/{id}/admins` | ADMIN / WORKSPACE_ADMIN | List workspace admins for a workspace (`WORKSPACE_ADMIN` restricted to workspaces they administer) |
| `POST`   | `/workspaces/{id}/admins` | ADMIN / WORKSPACE_ADMIN | Assign a user as workspace admin. Global `ADMIN` may promote any user (auto-grants the `WORKSPACE_ADMIN` role). A `WORKSPACE_ADMIN` may only add other users who **already** hold the `WORKSPACE_ADMIN` role — plain `MEMBER`/`USER` accounts cannot be elevated this way |
| `DELETE` | `/workspaces/{id}/admins/{userId}` | ADMIN / WORKSPACE_ADMIN | Remove a workspace admin (`WORKSPACE_ADMIN` restricted to workspaces they administer). Multiple workspace admins per workspace are supported |

**Workspace creation, visibility, and deletion semantics:** A `WORKSPACE_ADMIN` may create any number
of workspaces; on creation, `WorkspaceAdminService.autoAssignCreatorAsAdmin(...)` immediately creates a
`WorkspaceAdminMapping` linking the creator to the new workspace, so they can administer it right away
without any extra step. `WORKSPACE_ADMIN`s may change the `status` (visibility: `ENABLED`/`DRAFT`/`DISABLED`)
of workspaces they administer via `PUT /workspaces/{id}`, but not the `title` (renaming remains
`ADMIN`-only). Workspace deletion is intentionally restricted to global `ADMIN` (Super Admin) —
`WorkspaceController.deleteWorkspace` is annotated `@PreAuthorize("hasRole('ADMIN')")` and the frontend
hides/disables the Delete action for `WORKSPACE_ADMIN` users, so a `WORKSPACE_ADMIN` can never delete a
workspace even one they created or currently administer.

**Workspace Status Lifecycle:**

| Status     | Visible to Users | Visible to Admins | Participates in Jobs |
|------------|:----------------:|:-----------------:|:--------------------:|
| `ENABLED`  | ✅               | ✅                | ✅                   |
| `DRAFT`    | ❌               | ✅                | ✅                   |
| `DISABLED` | ❌               | ❌ (management only) | ❌              |

**Workspace Connectivity Status (dashboard colors):**

- `ONLINE` (green): connection probe succeeded
- `OFFLINE` (red): workspace became unreachable / token invalid / SDK request failed
- `UNKNOWN` (neutral): no probe result recorded yet

Connectivity status is persisted per workspace and refreshed:
1. whenever workspace credentials/details are created or updated,
2. hourly via a scheduled background job, and
3. immediately when filter execution/preview or metadata fetch encounters connectivity errors.

### Filters

Filter templates are visibility-scoped:
- Every template has an owner (`ownerEmail`) and a visibility flag (`isPublic`).
- Owners can set their own templates to **public** (visible/subscribable by all workspace users) or **private** (owner-only).
- Admins/workspace admins can manage all templates in the workspace.

| Method | Path                                           | Role required | Description                                     |
|--------|------------------------------------------------|:-------------:|-------------------------------------------------|
| `GET`  | `/workspaces/{id}/filters`                     | Any           | List accessible templates (shared admin + own private) |
| `POST` | `/workspaces/{id}/filters`                     | Any           | Create template with owner-controlled visibility (`isPublic`) |
| `PUT`  | `/workspaces/{id}/filters/{filterId}`          | Any (owner/admin) | Update owned private template or any template as admin |
| `POST` | `/workspaces/{id}/filters/parse-query-string`  | Any           | Validate and parse `fields=...&query=...` into structured criteria |
| `GET`  | `/workspaces/{id}/filters/{filterId}/query-string` | Any (accessible filter) | Export an accessible filter as copyable string |
| `POST` | `/workspaces/{id}/filters/{filterId}/execute`  | Any (accessible filter) | Execute an accessible filter against Octane and return entities |

`GET /filters` responses also include:
- `editable`: whether the current user can edit/delete the filter
- `publicTemplate`: whether the filter is visible to all users in the workspace
- `adminManaged`: legacy marker for old admin-shared templates (for backward compatibility)

**FilterCriteriaClause** (element of the `criteria` array):

```json
{
  "field": "defect_type",
  "operator": "IN",
  "values": ["Escaped"],
  "logicalOperator": "AND"
}
```

- `operator`: type-driven; supported values are `IN`, `NOT_IN`, `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `CONTAINS`, `NOT_CONTAINS`, `STARTS_WITH`, `IS_EMPTY`, `IS_NOT_EMPTY`
- `logicalOperator`: `AND` (default) or `OR` — controls how this clause is joined to the previous one. Ignored for the first clause.
- `referenceValues`: optional; when `true`, criteria are emitted as reference-ID clauses like `code_review_owner_udf EQ {id IN 8666}`

**Query-string format:** `fields=id,name&query=name EQ ^*Case360*^&order_by=creation_time&order_by_direction=DESC`

- `fields` is a comma-separated list of output fields
- `query` supports one or more clauses joined by `AND` or `;`
- `order_by` is optional and selects the field used for sorting
- `order_by_direction` is optional (`ASC` default, `DESC` supported)
- `query` can include `||` OR groups when all OR-joined expressions target the same field
- Accepted operators in query-string mode: `EQ`, `NEQ`, `IN`, `NOT_IN`, `GT`, `GTE`, `LT`, `LTE`; `EQ null`/`EQ {null}` map to `IS_EMPTY` and `NEQ null`/`NEQ {null}` map to `IS_NOT_EMPTY`
- Values can be wrapped with `^...^` and multiple values are comma-separated inside the wrapper
- Reference-ID clauses are supported, e.g. `owner EQ {id IN 8666}` and `phase EQ {id IN phase.defect.new,phase.defect.in_progress}`

### Octane Metadata (Easy Filter Builder)

These endpoints expose Octane metadata to power the visual, non-technical Easy Filter Builder UI. Both are accessible to any authenticated user.

| Method | Path                                                       | Description                                                      |
|--------|------------------------------------------------------------|------------------------------------------------------------------|
| `GET`  | `/workspaces/{id}/octane/fields?entityType=defect`         | Filterable fields with labels and type info (drives field dropdown) |
| `GET`  | `/workspaces/{id}/octane/field-values?fieldName=phase&entityType=defect&search=new&ids=phase.defect.new,phase.defect.in_progress` | Selectable values for a reference field (supports optional server-side search and exact ID resolution) |

For fields listed in `veemailer.octane.ui-bundle-field-names`, value resolution uses Octane `/list_nodes/ui_bundle` and selects the bundle named `{workspaceShortcode}_<field-derived-suffix>` (for example, `77BD_products`).

**OctaneFieldDto** (field metadata):
```json
{
  "name": "phase",
  "label": "Phase",
  "fieldType": "reference",
  "reference": true,
  "multiReference": false,
  "targetEntityType": "phase",
  "targetLogicalName": null
}
```

**OctaneFieldValueDto** (value option for reference fields):
```json
{ "id": "phase.defect.new", "name": "New" }
```

The backend automatically maps the `targetEntityType` to the correct Octane API collection:

| Target entity type | Octane API | Notes |
|---|---|---|
| `list_node` | `list_nodes?query="list_root={logical_name EQ '...'}"` | Severity, Priority, Defect type, etc. |
| `phase` | `phases` | Phase lifecycle states |
| `workspace_user` | `workspace_users` | Team members (full_name displayed) |
| `release` | `releases` | |
| `sprint` | `sprints` | |
| `product_area` | `product_areas` | |
| `team` | `teams` | |

### Subscriptions

All subscription endpoints require authentication. Users may only update/delete their own subscriptions (ownership enforced server-side). The on-demand `run` endpoint requires the `ADMIN` role.

Private filter subscriptions are enforced server-side:
- A private filter can only be subscribed by its owner.
- Group subscriptions can only use public filters.

**Subscription visibility:** `ADMIN` users see all subscriptions for the workspace; `MEMBER` users see only their own subscriptions plus any group subscriptions that include them. For group subscriptions, `MEMBER` users get a **View-only** experience (no edit, unsubscribe, enable/disable, or schedule changes). The frontend hides the "Recipient Email" column and labels the section "My Subscriptions" for `MEMBER` users.

| Method   | Path                                                      | Role required | Description                                              |
|----------|-----------------------------------------------------------|:-------------:|----------------------------------------------------------|
| `GET`    | `/workspaces/{id}/subscriptions`                          | Any           | ADMIN: all subscriptions; MEMBER: own subscriptions only |
| `POST`   | `/workspaces/{id}/subscriptions`                          | Any           | Subscribe to a filter template (adds Triage SLA threshold when applicable) |
| `POST`   | `/workspaces/{id}/subscriptions/bulk-group`               | ADMIN/WS_ADMIN | Bulk-subscribe all members of a recipient group        |
| `PUT`    | `/workspaces/{id}/subscriptions/{subId}`                  | Any (own)     | Update subscription schedule and threshold              |
| `DELETE` | `/workspaces/{id}/subscriptions/{subId}`                  | Any (own)     | Unsubscribe                                              |
| `POST`   | `/workspaces/{id}/subscriptions/{subId}/run`              | ADMIN         | Immediately send a notification email                    |

### Public Issue Reporting

The global footer includes a **Raise an issue** action that is available to authenticated and unauthenticated users.

| Method | Path      | Auth Required | Description |
|--------|-----------|:-------------:|-------------|
| `GET`  | `/issues/config` | No      | Get server-side issue upload limits (`maxUploadBytes`, `maxStoredScreenshotBytes`) used by the footer modal |
| `POST` | `/issues` | No            | Submit a `multipart/form-data` issue report with optional `message`, optional `reporterEmail`, and optional screenshot. Either `message` or screenshot is required. |

New issue reports are saved with default status `OPEN`. Uploaded screenshots are validated against server limits, and the backend attempts to compress oversized images before rejecting them.

### Admin — Issues Dashboard (`/api/admin/issues`)

All issue dashboard endpoints require the `ADMIN` role.

| Method  | Path                        | Role required | Description |
|---------|-----------------------------|:-------------:|-------------|
| `GET`   | `/admin/issues`             | ADMIN         | List all submitted issues (newest first), including screenshot preview payload when available |
| `PATCH` | `/admin/issues/{issueId}/status` | ADMIN    | Update issue status (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`) |

### Recipient Groups

Recipient groups (teams) are workspace-scoped collections of email addresses. Admins and workspace admins can create groups and bulk-subscribe them to filter templates.

| Method   | Path                                                              | Role required | Description                       |
|----------|-------------------------------------------------------------------|:-------------:|-----------------------------------|
| `GET`    | `/workspaces/{id}/recipient-groups`                               | Any           | List all groups for the workspace |
| `GET`    | `/workspaces/{id}/recipient-groups/{groupId}`                     | Any           | Get a single group                |
| `POST`   | `/workspaces/{id}/recipient-groups`                               | ADMIN/WS_ADMIN | Create a new group               |
| `PUT`    | `/workspaces/{id}/recipient-groups/{groupId}`                     | ADMIN/WS_ADMIN | Update group name/description/members |
| `DELETE` | `/workspaces/{id}/recipient-groups/{groupId}`                     | ADMIN/WS_ADMIN | Delete a group                   |
| `POST`   | `/workspaces/{id}/recipient-groups/{groupId}/members`             | ADMIN/WS_ADMIN | Add a member email to a group    |
| `DELETE` | `/workspaces/{id}/recipient-groups/{groupId}/members/{email}`     | ADMIN/WS_ADMIN | Remove a member email from a group |

### Admin — Notification Preferences (`/api/admin`)

All admin configuration endpoints require the `ADMIN` role. SMTP settings are stored in the database and used dynamically by the mail sender.

| Method | Path                                  | Role required | Description                          |
|--------|---------------------------------------|:-------------:|--------------------------------------|
| `GET`  | `/admin/notification-preferences`     | ADMIN         | Get SMTP config (password masked)    |
| `PUT`  | `/admin/notification-preferences`     | ADMIN         | Create/update SMTP config            |

**Password handling:** The `password` field in responses is always `"(unchanged)"`. On update, sending `"(unchanged)"` (or blank/null) preserves the existing password. A new value replaces it.

### Admin — Mail Analytics (`/api/admin/mail-analytics`)

All mail analytics endpoints require the `ADMIN` role. They provide aggregated statistics and a searchable history of all mail dispatches.

| Method | Path                                             | Role required | Description                                       |
|--------|--------------------------------------------------|:-------------:|---------------------------------------------------|
| `GET`  | `/admin/mail-analytics/summary`                  | ADMIN         | Summary stats (total mails, recipients, workspaces, top filter) |
| `GET`  | `/admin/mail-analytics/daily-volume`             | ADMIN         | Daily mail count for the given period             |
| `GET`  | `/admin/mail-analytics/daily-recipients`         | ADMIN         | Daily unique recipient count                      |
| `GET`  | `/admin/mail-analytics/workspace-distribution`   | ADMIN         | Mail count grouped by workspace                   |
| `GET`  | `/admin/mail-analytics/filter-usage`             | ADMIN         | Mail count grouped by filter template             |
| `GET`  | `/admin/mail-analytics/history`                  | ADMIN         | Paginated, filterable mail delivery log           |

**Query parameters (all endpoints):**

| Param            | Default | Description                                                    |
|------------------|---------|----------------------------------------------------------------|
| `days`           | `7`     | Lookback period in days (summary, daily-volume, daily-recipients, workspace-distribution, filter-usage) |

**Additional query parameters (`/history` only):**

| Param            | Default | Description                        |
|------------------|---------|------------------------------------|
| `workspaceId`    | —       | Filter by workspace UUID           |
| `recipientEmail` | —       | Filter by recipient (substring)    |
| `filterTitle`    | —       | Filter by filter template title    |
| `status`         | —       | `SUCCESS`, `FAILED`, or `SKIPPED`  |
| `from`           | —       | Start date (ISO date)              |
| `to`             | —       | End date (ISO date)                |
| `page`           | `0`     | Page number (0-indexed)            |
| `size`           | `20`    | Page size                          |

### Admin — User Management (`/api/admin/users`)

Superadmins can list all users, onboard users, resend pending invites, delete accounts, and
promote/demote a user's global role between `MEMBER` (plain user) and `WORKSPACE_ADMIN`.

| Method | Path              | Role required | Description                                                        |
|--------|-------------------|:-------------:|--------------------------------------------------------------------|
| `GET`  | `/admin/users`    | ADMIN         | List all users with role and subscription counts                   |
| `POST` | `/admin/users`    | ADMIN         | Onboard a new user (creates account + sends invite magic link to email) |
| `POST` | `/admin/users/{userId}/resend-invite` | ADMIN | Resend a magic-link invite for a user who has not completed onboarding |
| `DELETE` | `/admin/users/{userId}` | ADMIN   | Permanently delete a user account (self-delete is blocked)         |
| `PATCH` | `/admin/users/{userId}/role` | ADMIN | Promote/demote a user's global role (`{ "role": "WORKSPACE_ADMIN" }` or `{ "role": "MEMBER" }`); self and other super admins cannot be changed |

**Promote/demote semantics:** Demoting a `WORKSPACE_ADMIN` to `MEMBER` only revokes the global
`WORKSPACE_ADMIN` role — it deliberately does **not** delete any `WorkspaceAdminMapping` rows, so
existing per-workspace admin assignments are preserved untouched. All workspace administration
checks (`WorkspaceAdminService.canManageWorkspace`) require **both** the global `WORKSPACE_ADMIN`
role **and** a workspace-level mapping, so a demoted user immediately loses admin access even
though their workspace mappings still exist in the DB. Re-promoting the user instantly restores
their prior workspace admin access with no migration needed.

**Onboard user request body:** `{ "name": "Jane Smith", "email": "jane@company.com" }`

**User onboarding flow:**
1. Admin submits name + email via the Admin Panel → Users page.
2. Backend creates an account with a random temporary password and `mustSetPassword = true`.
3. An invite email containing a single-use, time-limited magic link is sent to the user.
4. The user opens the link, lands on `/accept-invite`, and chooses a new password (no OTP entry).
5. On success, the user is automatically logged in and `mustSetPassword` is cleared.

Users with a pending invite appear with an **amber "Pending invite"** badge in the Users table. Superadmins can use **Resend Invite** from the Users page to send a fresh magic link later, and users can also request a new link from the Accept Invite page.

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
VITE_ALLOW_CUSTOM_QUERY_STRING=false
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
veemailer.octane.ui-bundle-field-names=product_udf
veemailer.issues.max-screenshot-bytes=1048576
veemailer.issues.max-upload-bytes=5242880
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Authentication
app.auth.allowed-domains=company.com,int-company.com
app.auth.allow-multiple-sessions=false
app.auth.jwt.secret=<your-256-bit-secret>
app.auth.jwt.access-token-expiration-ms=900000
app.auth.jwt.refresh-token-expiration-ms=604800000
app.auth.invite-link.expiration-minutes=10
app.auth.invite-link.base-resend-seconds=30

# CORS — comma-separated list of allowed origins
app.cors.allowed-origins=http://localhost:5173,http://localhost:80,http://localhost
app.frontend.url=http://localhost:5173

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

### Triage SLA Custom Field

`Triage SLA` is a custom pseudo-field available in the filter builder and email output.

- Selecting `Triage SLA` auto-fetches `creation_time` from ValueEdge.
- Output format is `<traffic-light> <N> day(s) old`, for example: `🟡 3 days old`.
- Default age bands are configured in `backend/src/main/java/com/anushibinj/veemailer/service/TriageSlaPolicy.java`:
  - `4+` days → `🔴`
  - `3` days → `🟡`
  - `0-2` days → `🟢`
- When selected, preview/email results are sorted by age in descending order so oldest untriaged tickets appear first.
- When subscribing to a filter that includes `Triage SLA`, the subscription form asks for a threshold.
  - `Green` (default): send all triaged tickets
  - `Yellow`: send 3-day-old and older tickets
  - `Red`: send 4+-day-old tickets only

---

### Frontend — Environment Variables

| Variable                | Description                                                              | Example                                                    |
|-------------------------|--------------------------------------------------------------------------|------------------------------------------------------------||
| `VITE_BACKEND_ROOT_URL` | Base URL of the Spring Boot backend                                      | `http://localhost:8080`                                    |
| `VITE_ALLOW_CUSTOM_QUERY_STRING` | Enables query-string based filter creation and copy-string actions in the UI | `false` |
| `VITE_FOOTER_HTML`      | Optional HTML injected into the global app footer (sanitized before render) | `<div style="text-align:center">Powered by VE Mailer</div>` |

Create a `.env.local` file in the `frontend/` directory. Vite exposes only variables prefixed with `VITE_` to the browser bundle.

#### `VITE_ALLOW_CUSTOM_QUERY_STRING` — query-string filter workflow toggle

Controls whether the filter builder exposes query-string based workflow.

- `false` (default): hide all query-string UI (generate-from-string flow and copy-string action).
- `true`: show query-string workflow and related controls.

#### `VITE_FOOTER_HTML` — custom footer

Set this variable to any HTML snippet and it will be rendered inside the global footer at the bottom of every page.

```env
# Plain text footer
VITE_FOOTER_HTML=<div style="padding:8px;text-align:center;font-size:12px;">Powered by VE Mailer</div>

# Footer with a link
VITE_FOOTER_HTML=<div><a href="https://company.com">Company Portal</a> — Internal Use Only</div>
```

Allowed tags: `div`, `span`, `a`, `p`, `small`, `strong`, `em`, `br`, `ul`, `ol`, `li`.  
Blocked automatically: `<script>`, `<iframe>`, inline event handlers (`onclick`, `onerror`, …), and `javascript:` URLs.

---

### Docker — Timezone (`TZ`)

The Docker image defaults to `Asia/Calcutta`. Override the timezone at runtime via the `TZ` environment variable; the JVM timezone is set to match via `-Duser.timezone=$TZ`.

| Variable | Default         | Description                                      |
|----------|-----------------|--------------------------------------------------|
| `TZ`     | `Asia/Calcutta` | IANA timezone applied to both the OS and the JVM |

**Examples:**

```bash
# Default (Asia/Calcutta)
docker run ve-mailer-backend

# UTC
docker run -e TZ=UTC ve-mailer-backend

# UK / Europe
docker run -e TZ=Europe/London ve-mailer-backend

# US East Coast
docker run -e TZ=America/New_York ve-mailer-backend
```

**docker-compose override:**

```yaml
environment:
  TZ: Europe/London
```

The timezone controls:

- Log timestamps
- Spring `@Scheduled` cron executions (`PollingService`)
- Mail dispatch timestamps

On startup the active timezone is printed to the log:

```
Application timezone: Asia/Calcutta
```

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

## Frontend URL Structure

All navigation in the frontend is URL-based (React Router). Bookmark or share any page directly.

| URL                                    | View                                            | Auth Required |
|:---------------------------------------|:------------------------------------------------|:-------------|
| `/login`                               | Login page                                      | No           |
| `/signup`                              | Sign up page                                    | No           |
| `/verify-signup`                       | OTP verification for new accounts               | No           |
| `/forgot-password`                     | Request password reset                          | No           |
| `/reset-password`                      | Reset password with OTP                         | No           |
| `/accept-invite`                       | Accept workspace invite and set password        | No           |
| `/`                                    | Workspace picker (landing view)                 | Yes          |
| `/workspace/:workspaceId`              | Workspace dashboard (subscriptions)             | Yes          |
| `/workspace/:workspaceId/filters`      | Filter template management                      | Yes          |
| `/workspace/:workspaceId/groups`       | Recipient group management                      | Yes          |
| `/admin`                               | Admin control panel (defaults to first tab)     | Admin only   |
| `/admin?tab=workspaces`                | Workspace management tab                        | Admin only   |
| `/admin?tab=recipient-groups`          | Recipient groups management tab                 | Admin only   |
| `/admin?tab=notification-preferences`  | SMTP notification settings tab                  | Super admin  |
| `/admin?tab=ai-preferences`            | AI model configuration tab                      | Super admin  |
| `/admin?tab=mail-analytics`            | Mail delivery analytics tab                     | Super admin  |
| `/admin?tab=users`                     | User management tab                             | Super admin  |
| `/admin?tab=general`                   | General settings tab                            | Super admin  |
| `/admin?tab=issues`                    | Issue reports tab                               | Super admin  |

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

Filter templates are the core building block. Each filter is stored as structured data (entity type, fields, criteria) and can be created via:

1. **Easy Filter Builder** (recommended) — a visual, non-technical UI where:
   - Fields are selected from a searchable dropdown populated live from Octane's `/metadata/fields` API
   - The **Fields to Fetch** picker is a searchable tag/badge flow backed by live metadata for the selected entity type (instead of a static frontend list), while keeping custom pseudo-fields like **✨ AI Summary** and **Triage SLA**
   - Selected field chips are drag-reorderable, and that exact order is reused in both preview output and email table columns
   - Special pseudo-fields (✨ AI Summary, 🚦 Triage SLA) are shown in the same field-selection panel for a single, unified picker experience
   - An **Order by** selector appears immediately after **Fields to Fetch**, using the same metadata-driven field options
   - Operators are selected dynamically from each field's runtime metadata (`fieldType` + `reference`), so numeric/date/boolean/text/reference fields each get relevant operator sets
   - Values for reference fields (phase, owner, severity, etc.) are selected from a searchable multi-select populated from the corresponding Octane entity list
   - Date fields support relative presets (Today, Yesterday, Last 24 hours, Last 7 days, Last 30 days, and configurable Last X days) plus custom date/time input
   - Conditions can be joined with **AND** or **OR** using a per-row connector dropdown

2. **Query-string import** (power users, optional) — paste a compact `fields=...&query=...` string that the backend validates and converts into structured clauses

A filter has:

1. **Entity type** — filter scope selected in UI:
   - `backlog_items` → `subtype IN defect,story,quality_story`
   - `epic` → `subtype EQ epic`
   - `feature` → `subtype EQ feature`
2. **Fields** — which fields to return in the result set (e.g. `["id", "name", "phase", "owner"]`)
3. **Order by (optional)** — a metadata-backed field name plus direction (`ASC`/`DESC`) used to sort query results
4. **Criteria** — an array of clauses that are AND/OR-joined to build the Octane SDK query
5. **Ownership (`ownerEmail`)**:
   - `null` for admin-created shared templates
   - user email for private member templates

UI visibility cues:
- In user view, shared templates are marked **Admin template** and user-owned ones are marked **Private template**.
- In admin/workspace-admin view, each filter also shows **Created by** (`ownerEmail` when private, otherwise `Admin`).

#### Easy Filter Builder — How the metadata APIs work

When the filter form opens, the `SmartFilterRow` component calls:
- `GET /octane/fields?entityType=defect` → returns all filterable fields with human-readable labels, field types, and reference target info
- When editing an existing reference clause, selected IDs are resolved with `GET /octane/field-values?...&ids=<comma-separated-ids>` so saved IDs always map to display names even if they are outside the default list window
- When the user opens a reference dropdown, the UI fetches the initial value window with `GET /octane/field-values?...&search=*`

The `OctaneMetadataService` handles the mapping:
- `list_node` targets → queries `list_nodes?query="list_root={logical_name EQ '...'}"` (e.g. for severity, priority)
- `phase` targets → queries `phases` scoped by entity type (deduplicated fallback if scope fails)
- `workspace_user` targets → queries `workspace_users` (team members)
- `release`/`sprint`/`team`/`product_area` → queries the respective entity list

The value picker first filters the currently loaded list in the browser. If a typed term has no local matches, it automatically calls `field-values` with `search=<term>` so Octane can search beyond the initial result window (for example, owner lists larger than 1000 users).

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
| 6 | Passes results to `NotificationService`; when a filter returns 0 tickets, no email is sent and an audit "skipped (no tickets)" record is stored per intended recipient |

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
