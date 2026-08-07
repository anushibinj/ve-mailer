# Database

## ai_preferences

Mapped entity: `AiPreferences`

| Column | Type | PK | FK |
|---|---|---|---|
| api_key | VARCHAR(500) |  |  |
| base_url | VARCHAR(255) |  |  |
| chat_completions_path | VARCHAR(255) |  |  |
| id | UUID |  |  |
| model | VARCHAR(255) |  |  |

Relates to: PRIMARY, UUID

## app_users

Mapped entity: `AppUser`

| Column | Type | PK | FK |
|---|---|---|---|
| created_at | TIMESTAMP |  |  |
| email | VARCHAR(255) |  |  |
| enabled | BOOLEAN |  |  |
| id | UUID |  |  |
| name | VARCHAR(255) |  |  |
| password_hash | VARCHAR(255) |  |  |
| updated_at | TIMESTAMP |  |  |

Relates to: UUID

## email_subscribers

Mapped entity: `EmailSubscriber`

| Column | Type | PK | FK |
|---|---|---|---|
| filter_id | UUID |  |  |
| frequency | VARCHAR(50) |  |  |
| id | UUID |  |  |
| recipient_email | VARCHAR(255) |  |  |
| schedule_type | VARCHAR(50) |  |  |
| status | VARCHAR(50) |  |  |
| workspace_id | UUID |  |  |

Relates to: Frequency, RecipientGroup, ScheduleType, Status, TriageSlaThreshold, UUID, Workspace, filters, workspaces

## filters

Mapped entity: `Filter`

| Column | Type | PK | FK |
|---|---|---|---|
| criteria | TEXT |  |  |
| description | VARCHAR(255) |  |  |
| entity_type | VARCHAR(255) |  |  |
| fields | TEXT |  |  |
| id | UUID |  |  |
| title | VARCHAR(255) |  |  |
| workspace_id | UUID |  |  |

Relates to: UUID, Workspace, workspaces

## general_settings

Mapped entity: `GeneralSettings`

| Column | Type | PK | FK |
|---|---|---|---|
| id | UUID |  |  |
| query_limit | INT |  |  |

Relates to: UUID

## invite_magic_links

Mapped entity: `InviteMagicLink`

| Column | Type | PK | FK |
|---|---|---|---|
| email | VARCHAR(255) |  |  |
| expires_at | TIMESTAMP |  |  |
| id | UUID |  |  |
| last_sent_at | TIMESTAMP |  |  |
| resend_count | INTEGER |  |  |
| token_hash | VARCHAR(255) |  |  |
| used_at | TIMESTAMP |  |  |

Relates to: UUID

## issue_reports

Mapped entity: `IssueReport`

| Column | Type | PK | FK |
|---|---|---|---|
| created_at | TIMESTAMP |  |  |
| id | UUID |  |  |
| message | TEXT |  |  |
| reporter_email | VARCHAR(255) |  |  |
| screenshot_content_type | VARCHAR(255) |  |  |
| screenshot_data | BYTEA |  |  |
| screenshot_file_name | VARCHAR(255) |  |  |
| status | VARCHAR(30) |  |  |
| updated_at | TIMESTAMP |  |  |

Relates to: IssueStatus, UUID

## mail_audit_log

Mapped entity: `MailAuditLog`

| Column | Type | PK | FK |
|---|---|---|---|
| delivery_status | VARCHAR(20) |  |  |
| duration_ms | BIGINT |  |  |
| failure_reason | VARCHAR(2000) |  |  |
| filter_template_id | UUID |  |  |
| filter_title | VARCHAR(255) |  |  |
| id | UUID |  |  |
| mail_subject | VARCHAR(500) |  |  |
| recipient_email | VARCHAR(255) |  |  |
| sent_at | TIMESTAMP |  |  |
| subscription_id | UUID |  |  |
| ticket_count | INTEGER |  |  |
| user_id | UUID |  |  |
| workspace_id | UUID |  |  |
| workspace_title | VARCHAR(255) |  |  |

Relates to: DeliveryStatus, UUID

## notification_preferences

Mapped entity: `NotificationPreferences`

| Column | Type | PK | FK |
|---|---|---|---|
| host | VARCHAR(255) |  |  |
| id | UUID |  |  |
| password | VARCHAR(255) |  |  |
| port | INT |  |  |
| start_tls_enabled | BOOLEAN |  |  |
| username | VARCHAR(255) |  |  |

Relates to: PRIMARY, UUID

## otp_requests

Mapped entity: `OtpRequest`

| Column | Type | PK | FK |
|---|---|---|---|
| action_type | VARCHAR(50) |  |  |
| email | VARCHAR(255) |  |  |
| expires_at | TIMESTAMP |  |  |
| id | UUID |  |  |
| otp_hash | VARCHAR(255) |  |  |
| payload | TEXT |  |  |

Relates to: ActionType, UUID

## recipient_group_members

| Column | Type | PK | FK |
|---|---|---|---|
| group_id | UUID |  |  |
| member_email | VARCHAR(255) |  |  |

Relates to: recipient_groups

## recipient_groups

| Column | Type | PK | FK |
|---|---|---|---|
| created_at | TIMESTAMP |  |  |
| created_by | VARCHAR(255) |  |  |
| description | VARCHAR(500) |  |  |
| id | UUID |  |  |
| name | VARCHAR(255) |  |  |
| workspace_id | UUID |  |  |

Relates to: workspaces

## refresh_tokens

Mapped entity: `RefreshToken`

| Column | Type | PK | FK |
|---|---|---|---|
| expires_at | TIMESTAMP |  |  |
| id | UUID |  |  |
| revoked | BOOLEAN |  |  |
| token | VARCHAR(255) |  |  |
| user_id | UUID |  |  |

Relates to: UUID, app_users

## roles

Mapped entity: `Role`

| Column | Type | PK | FK |
|---|---|---|---|
| id | UUID |  |  |
| role_name | VARCHAR(255) |  |  |

Relates to: UUID

## subscriber_scheduled_hours

| Column | Type | PK | FK |
|---|---|---|---|
| scheduled_hour | INTEGER |  |  |
| subscriber_id | UUID |  |  |

Relates to: email_subscribers

## user_roles

| Column | Type | PK | FK |
|---|---|---|---|
| role_id | UUID |  |  |
| user_id | UUID |  |  |

Relates to: app_users, roles

## workspace_admins

| Column | Type | PK | FK |
|---|---|---|---|
| created_at | TIMESTAMP |  |  |
| created_by | VARCHAR(255) |  |  |
| id | UUID |  |  |
| user_id | UUID |  |  |
| workspace_id | UUID |  |  |

Relates to: app_users, workspaces

## workspaces

| Column | Type | PK | FK |
|---|---|---|---|
| client_id | VARCHAR(255) |  |  |
| client_key | VARCHAR(255) |  |  |
| id | UUID |  |  |
| shared_space_id | VARCHAR(255) |  |  |
| title | VARCHAR(255) |  |  |
| workspace_id | VARCHAR(255) |  |  |

