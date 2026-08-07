# Frontend

## Pages

| Name | File | API Calls |
|---|---|---|
| PageHeader | frontend/src/components/ui/PageHeader.tsx |  |
| SectionHeader | frontend/src/components/ui/PageHeader.tsx |  |
| AcceptInvitePage | frontend/src/pages/AcceptInvitePage.tsx |  |
| ForgotPasswordPage | frontend/src/pages/ForgotPasswordPage.tsx |  |
| LoginPage | frontend/src/pages/LoginPage.tsx |  |
| ResetPasswordPage | frontend/src/pages/ResetPasswordPage.tsx |  |
| SignupPage | frontend/src/pages/SignupPage.tsx |  |
| VerifySignupPage | frontend/src/pages/VerifySignupPage.tsx |  |
| AdminControlPanel | frontend/src/pages/admin/AdminControlPanel.tsx |  |
| AiPreferencesPage | frontend/src/pages/admin/AiPreferencesPage.tsx |  |
| GeneralSettingsPage | frontend/src/pages/admin/GeneralSettingsPage.tsx |  |
| IssuesPage | frontend/src/pages/admin/IssuesPage.tsx |  |
| MailAnalyticsPage | frontend/src/pages/admin/MailAnalyticsPage.tsx |  |
| StatCard | frontend/src/pages/admin/MailAnalyticsPage.tsx |  |
| NotificationPreferencesPage | frontend/src/pages/admin/NotificationPreferencesPage.tsx |  |
| AddMemberPicker | frontend/src/pages/admin/RecipientGroupsPage.tsx |  |
| GroupFormModal | frontend/src/pages/admin/RecipientGroupsPage.tsx |  |
| RecipientGroupsPage | frontend/src/pages/admin/RecipientGroupsPage.tsx |  |
| OnboardModal | frontend/src/pages/admin/UsersPage.tsx |  |
| SortIcon | frontend/src/pages/admin/UsersPage.tsx |  |
| UsersPage | frontend/src/pages/admin/UsersPage.tsx |  |
| WorkspaceAdminManager | frontend/src/pages/admin/WorkspaceAdminManager.tsx |  |
| WorkspaceManagementPage | frontend/src/pages/admin/WorkspaceManagementPage.tsx |  |

## Components

| Name | File | API Calls |
|---|---|---|
| App | frontend/src/App.tsx |  |
| AppBreadcrumbs | frontend/src/App.tsx |  |
| AppHeader | frontend/src/App.tsx |  |
| AppShell | frontend/src/App.tsx |  |
| ThemeToggle | frontend/src/App.tsx |  |
| WorkspaceShell | frontend/src/App.tsx |  |
| AppFooter | frontend/src/components/AppFooter.tsx | ${backendUrl}/api/about |
| AuthShell | frontend/src/components/AuthShell.tsx |  |
| ConfirmDialog | frontend/src/components/ConfirmDialog.tsx |  |
| EditSubscriptionModal | frontend/src/components/EditSubscriptionModal.tsx |  |
| FieldBadgeWithPopover | frontend/src/components/FilterBuilderView.tsx |  |
| FilterBuilderView | frontend/src/components/FilterBuilderView.tsx |  |
| IssueReportModal | frontend/src/components/IssueReportModal.tsx |  |
| LandingView | frontend/src/components/LandingView.tsx |  |
| LoadingPlaceholder | frontend/src/components/LoadingPlaceholder.tsx |  |
| ProtectedRoute | frontend/src/components/ProtectedRoute.tsx |  |
| GroupFormModal | frontend/src/components/RecipientGroupsView.tsx |  |
| RecipientGroupsView | frontend/src/components/RecipientGroupsView.tsx |  |
| FieldSelect | frontend/src/components/SmartFilterRow.tsx |  |
| SmartFilterRow | frontend/src/components/SmartFilterRow.tsx |  |
| ValuePicker | frontend/src/components/SmartFilterRow.tsx |  |
| RequiredMark | frontend/src/components/SubscriptionFormModal.tsx |  |
| SubscriptionFormModal | frontend/src/components/SubscriptionFormModal.tsx |  |
| SummaryRow | frontend/src/components/WorkspaceCreationWizard.tsx |  |
| SummarySection | frontend/src/components/WorkspaceCreationWizard.tsx |  |
| WorkspaceCreationWizard | frontend/src/components/WorkspaceCreationWizard.tsx |  |
| SortIcon | frontend/src/components/WorkspaceDashboard.tsx |  |
| SortableHeader | frontend/src/components/WorkspaceDashboard.tsx |  |
| WorkspaceDashboard | frontend/src/components/WorkspaceDashboard.tsx |  |
| WorkspaceFormModal | frontend/src/components/WorkspaceFormModal.tsx |  |
| StubAppShell | frontend/src/components/__tests__/AppShell.test.tsx |  |
| Badge | frontend/src/components/ui/Badge.tsx |  |
| ConnectivityBadge | frontend/src/components/ui/Badge.tsx |  |
| Button | frontend/src/components/ui/Button.tsx |  |
| Card | frontend/src/components/ui/Card.tsx |  |
| CardBody | frontend/src/components/ui/Card.tsx |  |
| CardHeader | frontend/src/components/ui/Card.tsx |  |
| EmptyState | frontend/src/components/ui/EmptyState.tsx |  |
| ErrorState | frontend/src/components/ui/EmptyState.tsx |  |
| Input | frontend/src/components/ui/Input.tsx |  |
| Select | frontend/src/components/ui/Input.tsx |  |
| Textarea | frontend/src/components/ui/Input.tsx |  |
| SearchInput | frontend/src/components/ui/SearchInput.tsx |  |
| Skeleton | frontend/src/components/ui/Skeleton.tsx |  |
| SkeletonCard | frontend/src/components/ui/Skeleton.tsx |  |
| SkeletonCardGrid | frontend/src/components/ui/Skeleton.tsx |  |
| SkeletonDashboard | frontend/src/components/ui/Skeleton.tsx |  |
| SkeletonRow | frontend/src/components/ui/Skeleton.tsx |  |
| SkeletonText | frontend/src/components/ui/Skeleton.tsx |  |
| TableActionButton | frontend/src/components/ui/TableActionButton.tsx |  |
| Tooltip | frontend/src/components/ui/Tooltip.tsx |  |
| DuplicateWorkspaceConflictBanner | frontend/src/components/workspaceFormShared.tsx |  |
| ThemeProvider | frontend/src/contexts/ThemeContext.tsx |  |
| AuthProvider | frontend/src/hooks/useAuth.tsx |  |

## Routes

| Path | Component | File |
|---|---|---|
| * | Navigate | frontend/src/App.tsx |
| * | Navigate | frontend/src/App.tsx |
| / | ProtectedRoute | frontend/src/App.tsx |
| /accept-invite | Suspense | frontend/src/App.tsx |
| /admin | ProtectedRoute | frontend/src/App.tsx |
| /forgot-password | Suspense | frontend/src/App.tsx |
| /login | Suspense | frontend/src/App.tsx |
| /reset-password | Suspense | frontend/src/App.tsx |
| /signup | Suspense | frontend/src/App.tsx |
| /verify-signup | Suspense | frontend/src/App.tsx |
| /workspace/:workspaceId/* | ProtectedRoute | frontend/src/App.tsx |
| filters | Suspense | frontend/src/App.tsx |
| groups | Suspense | frontend/src/App.tsx |

