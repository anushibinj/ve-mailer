package com.anushibinj.veemailer.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final DynamicMailSenderService dynamicMailSenderService;
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    public void sendOtpEmail(String to, String otp) {
        try {
            Session session = dynamicMailSenderService.getSession();
            String from = dynamicMailSenderService.getFromAddress();

            String body =
                "<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:#334155;\">" +
                "Use the one-time code below to sign in to VE Mailer. " +
                "This code will expire in 10 minutes." +
                "</p>" +
                "<div style=\"display:inline-block;background-color:#eef2ff;border:1px solid #c7d2fe;" +
                "border-radius:8px;padding:16px 32px;margin:0 0 20px;\">" +
                "<span style=\"font-size:28px;font-weight:700;letter-spacing:0.15em;color:#4f46e5;" +
                "font-family:monospace;\">" + esc(otp) + "</span>" +
                "</div>" +
                "<p style=\"margin:0;font-size:12px;color:#94a3b8;\">" +
                "If you did not request this code, you can safely ignore this email." +
                "</p>";

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("[ve-mailer] Your sign-in code", "UTF-8");
            message.setContent(buildEmailShell("Your sign-in code", body), "text/html; charset=UTF-8");
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    @Async
    public void sendInviteMagicLinkEmail(String to, String name, String magicLink, int expiresInMinutes) {
        try {
            Session session = dynamicMailSenderService.getSession();
            String from = dynamicMailSenderService.getFromAddress();

            String body =
                "<p style=\"margin:0 0 16px;font-size:14px;line-height:1.6;color:#334155;\">" +
                "Hello <strong style=\"color:#1e293b;\">" + esc(name) + "</strong>,<br>" +
                "You have been invited to use VE Mailer. Click the button below to finish setting up your account." +
                "</p>" +
                "<div style=\"margin:24px 0;\">" +
                "<a href=\"" + esc(magicLink) + "\" " +
                "style=\"display:inline-block;background-color:#4f46e5;" +
                "color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;" +
                "padding:12px 28px;border-radius:8px;letter-spacing:0.02em;\">" +
                "Accept invitation &#8594;</a>" +
                "</div>" +
                "<p style=\"margin:0 0 8px;font-size:12px;color:#334155;\">" +
                "This link expires in <strong style=\"color:#1e293b;\">" + expiresInMinutes + " minutes</strong> " +
                "and can be used only once." +
                "</p>" +
                "<p style=\"margin:0 0 16px;font-size:12px;color:#64748b;\">" +
                "If the button doesn't work, copy and paste this URL into your browser:<br>" +
                "<span style=\"color:#4f46e5;word-break:break-all;\">" + esc(magicLink) + "</span>" +
                "</p>" +
                "<p style=\"margin:0;font-size:12px;color:#94a3b8;\">" +
                "Didn't expect this invitation? You can safely ignore this email.<br>" +
                "You can also request a fresh link from: " +
                "<a href=\"" + esc(frontendUrl) + "/accept-invite\" style=\"color:#4f46e5;\">" +
                esc(frontendUrl) + "/accept-invite</a>" +
                "</p>";

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("[ve-mailer] You've been invited to VE Mailer", "UTF-8");
            message.setContent(buildEmailShell("You've been invited", body), "text/html; charset=UTF-8");
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send invite magic link email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send invite magic link email", e);
        }
    }

    @Async
    public void sendOnboardingNotificationToAdmins(String newUserName, String newUserEmail, List<String> adminEmails) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            return;
        }
        try {
            Session session = dynamicMailSenderService.getSession();
            String from = dynamicMailSenderService.getFromAddress();
            String recipientList = String.join(",", adminEmails);

            String body =
                "<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:#334155;\">" +
                "A new user has completed onboarding on VE Mailer." +
                "</p>" +
                "<div style=\"background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;" +
                "padding:16px 20px;margin:0 0 20px;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">" +
                "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;\">Name</td>" +
                "<td style=\"padding:4px 0;font-size:14px;color:#1e293b;\">" + esc(newUserName) + "</td></tr>" +
                "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;\">Email</td>" +
                "<td style=\"padding:4px 0;font-size:14px;color:#1e293b;\">" + esc(newUserEmail) + "</td></tr>" +
                "</table>" +
                "</div>" +
                "<p style=\"margin:0;font-size:12px;color:#64748b;\">" +
                "Manage users in the admin panel: " +
                "<a href=\"" + esc(frontendUrl) + "/admin\" style=\"color:#4f46e5;\">" +
                esc(frontendUrl) + "/admin</a>" +
                "</p>";

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientList));
            message.setSubject("[ve-mailer] New user onboarded: " + newUserName, "UTF-8");
            message.setContent(buildEmailShell("New user onboarded", body), "text/html; charset=UTF-8");
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send onboarding notification for user {} to admins: {}", newUserEmail, e.getMessage());
            // Non-critical — do not rethrow; user onboarding should not fail because of this
        }
    }

    @Async
    public void sendRoleChangeNotification(
            String userName,
            String userEmail,
            String previousRoleName,
            String newRoleName,
            String workspaceId,
            String workspaceTitle) {
        try {
            Session session = dynamicMailSenderService.getSession();
            String from = dynamicMailSenderService.getFromAddress();

            boolean hasWorkspace = workspaceId != null && !workspaceId.isBlank();
            String workspaceRow = hasWorkspace
                ? "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;\">Workspace</td>" +
                  "<td style=\"padding:4px 0;font-size:14px;color:#1e293b;\">" + esc(workspaceTitle) + "</td></tr>"
                : "";
            String workspaceLink = hasWorkspace
                ? "<div style=\"margin:20px 0;\">" +
                  "<a href=\"" + esc(frontendUrl) + "/workspace/" + esc(workspaceId) + "\" " +
                  "style=\"display:inline-block;background-color:#4f46e5;" +
                  "color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;" +
                  "padding:12px 28px;border-radius:8px;letter-spacing:0.02em;\">" +
                  "Go to " + esc(workspaceTitle) + " &#8594;</a>" +
                  "</div>"
                : "";

            String body =
                "<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:#334155;\">" +
                "Your VE Mailer role has been updated" + (hasWorkspace ? " for the workspace below." : ".") +
                "</p>" +
                "<div style=\"background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;" +
                "padding:16px 20px;margin:0 0 20px;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">" +
                "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;\">Name</td>" +
                "<td style=\"padding:4px 0;font-size:14px;color:#1e293b;\">" + esc(userName) + "</td></tr>" +
                "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;\">Email</td>" +
                "<td style=\"padding:4px 0;font-size:14px;color:#1e293b;\">" + esc(userEmail) + "</td></tr>" +
                "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;\">Previous role</td>" +
                "<td style=\"padding:4px 0;font-size:14px;color:#1e293b;\">" + esc(previousRoleName) + "</td></tr>" +
                "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;\">New role</td>" +
                "<td style=\"padding:4px 0;font-size:14px;color:#1e293b;\">" + esc(newRoleName) + "</td></tr>" +
                workspaceRow +
                "</table>" +
                "</div>" +
                workspaceLink;

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(userEmail));
            message.setSubject("[ve-mailer] Role updated: " + previousRoleName + " to " + newRoleName, "UTF-8");
            message.setContent(buildEmailShell("Role updated", body), "text/html; charset=UTF-8");
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send role change notification to {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Notifies all Super Admins (users holding the ADMIN role) that a newly created workspace
     * needs manual review because its shortcode could not be determined automatically during
     * creation (workspaceShortcode == "UNKNOWN"). The workspace is created as DRAFT and cannot
     * be enabled until a Super Admin corrects the shortcode. Never sent when the shortcode was
     * successfully parsed.
     */
    @Async
    public void sendWorkspaceDraftReviewNotificationToAdmins(
            String workspaceTitle, String rootUrl, String sharedSpaceId, String workspaceId,
            String creatorEmail, java.time.Instant createdAt, List<String> adminEmails) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            return;
        }
        try {
            Session session = dynamicMailSenderService.getSession();
            String from = dynamicMailSenderService.getFromAddress();
            String recipientList = String.join(",", adminEmails);
            String createdAtDisplay = createdAt != null
                    ? java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a 'UTC'")
                        .withZone(java.time.ZoneOffset.UTC).format(createdAt)
                    : "Unknown";

            String body =
                "<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:#334155;\">" +
                "A workspace was just created with a shortcode that could not be determined automatically. " +
                "It has been created as <strong style=\"color:#1e293b;\">Draft</strong> and cannot be enabled " +
                "until the shortcode is corrected." +
                "</p>" +
                "<div style=\"background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;" +
                "padding:16px 20px;margin:0 0 20px;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%;\">" +
                tableRow("Workspace Title", workspaceTitle) +
                tableRow("Root URL", rootUrl) +
                tableRow("Shared Space ID", sharedSpaceId) +
                tableRow("Workspace ID", workspaceId) +
                tableRow("Creator", creatorEmail) +
                tableRow("Creation Time", createdAtDisplay) +
                "</table>" +
                "</div>" +
                "<p style=\"margin:0;font-size:12px;color:#64748b;\">" +
                "Review and correct the workspace shortcode in the admin panel: " +
                "<a href=\"" + esc(frontendUrl) + "/admin?tab=workspaces\" style=\"color:#4f46e5;\">" +
                esc(frontendUrl) + "/admin?tab=workspaces</a>" +
                "</p>";

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientList));
            message.setSubject("[ve-mailer] Workspace pending review: " + workspaceTitle, "UTF-8");
            message.setContent(buildEmailShell("Workspace pending review", body), "text/html; charset=UTF-8");
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send workspace draft review notification for '{}' to admins: {}",
                    workspaceTitle, e.getMessage());
            // Non-critical — workspace creation should not fail because of this
        }
    }

    /**
     * Notifies all Super Admins (users holding the ADMIN role) that a Workspace Admin created a
     * new workspace. Unlike {@link #sendWorkspaceDraftReviewNotificationToAdmins}, this is a
     * purely informational FYI (no action required) and is only sent when the workspace was
     * created with a successfully-detected shortcode — if the shortcode is UNKNOWN, the draft
     * review notification already covers the same creator/workspace details plus a call to action,
     * so both emails are never sent for the same workspace.
     */
    @Async
    public void sendWorkspaceCreatedNotificationToAdmins(
            String workspaceTitle, String rootUrl, String sharedSpaceId, String workspaceId,
            String creatorEmail, java.time.Instant createdAt, List<String> adminEmails) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            return;
        }
        try {
            Session session = dynamicMailSenderService.getSession();
            String from = dynamicMailSenderService.getFromAddress();
            String recipientList = String.join(",", adminEmails);
            String createdAtDisplay = createdAt != null
                    ? java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a 'UTC'")
                        .withZone(java.time.ZoneOffset.UTC).format(createdAt)
                    : "Unknown";

            String body =
                "<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:#334155;\">" +
                "A Workspace Admin created a new workspace on VE Mailer." +
                "</p>" +
                "<div style=\"background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;" +
                "padding:16px 20px;margin:0 0 20px;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%;\">" +
                tableRow("Workspace Title", workspaceTitle) +
                tableRow("Root URL", rootUrl) +
                tableRow("Shared Space ID", sharedSpaceId) +
                tableRow("Workspace ID", workspaceId) +
                tableRow("Creator", creatorEmail) +
                tableRow("Creation Time", createdAtDisplay) +
                "</table>" +
                "</div>" +
                "<p style=\"margin:0;font-size:12px;color:#64748b;\">" +
                "View the workspace in the admin panel: " +
                "<a href=\"" + esc(frontendUrl) + "/admin?tab=workspaces\" style=\"color:#4f46e5;\">" +
                esc(frontendUrl) + "/admin?tab=workspaces</a>" +
                "</p>";

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientList));
            message.setSubject("[ve-mailer] New workspace created: " + workspaceTitle, "UTF-8");
            message.setContent(buildEmailShell("New workspace created", body), "text/html; charset=UTF-8");
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send workspace created notification for '{}' to admins: {}",
                    workspaceTitle, e.getMessage());
            // Non-critical — workspace creation should not fail because of this
        }
    }

    /** Renders a single label/value row for the summary tables used by admin notification emails. */
    private String tableRow(String label, String value) {
        return "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;" +
                "text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;vertical-align:top;\">" +
                esc(label) + "</td>" +
                "<td style=\"padding:4px 0;font-size:14px;color:#1e293b;word-break:break-all;\">" +
                esc(value) + "</td></tr>";
    }

    /**
     * Wraps a content block in the standard light-themed VE Mailer email shell.
     * Light mode by default — email clients apply their own dark mode conversion if enabled.
     * Table-based layout with {@code bgcolor} attributes ensures backgrounds survive across
     * all major email clients including Outlook Windows.
     */
    private String buildEmailShell(String title, String bodyHtml) {
        return "<!DOCTYPE html><html lang=\"en\"><head>" +
               "<meta charset=\"UTF-8\">" +
               "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
               "</head>" +
               "<body style=\"margin:0;padding:0;background-color:#f1f5f9;" +
               "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;\">" +
               "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"#f1f5f9\" " +
               "style=\"background-color:#f1f5f9;\"><tr>" +
               "<td align=\"center\" bgcolor=\"#f1f5f9\" style=\"padding:32px 16px;background-color:#f1f5f9;\">" +
               "<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" bgcolor=\"#ffffff\" " +
               "style=\"max-width:560px;width:100%;background-color:#ffffff;" +
               "border-radius:12px;border:1px solid #e2e8f0;\">" +
               "<tr><td bgcolor=\"#4f46e5\" " +
               "style=\"background-color:#4f46e5;" +
               "padding:18px 28px;border-radius:12px 12px 0 0;\">" +
               "<span style=\"color:#ffffff;font-size:16px;font-weight:700;letter-spacing:-0.3px;\">" +
               "&#9993;&nbsp;&nbsp;VE Mailer</span></td></tr>" +
               "<tr><td bgcolor=\"#ffffff\" style=\"background-color:#ffffff;padding:28px;\">" +
               "<h2 style=\"margin:0 0 20px;font-size:18px;font-weight:700;color:#1e293b;" +
               "letter-spacing:-0.3px;\">" + esc(title) + "</h2>" +
               bodyHtml +
               "</td></tr>" +
               "<tr><td bgcolor=\"#f1f5f9\" style=\"background-color:#f1f5f9;padding:12px 28px;" +
               "border-top:1px solid #e2e8f0;text-align:center;\">" +
               "<span style=\"font-size:11px;color:#94a3b8;\">VE Mailer &middot; Automated notification system</span>" +
               "</td></tr>" +
               "</table></td></tr></table>" +
               "</body></html>";
    }

    /** Minimal HTML escaping to prevent broken markup when interpolating untrusted strings. */
    private String esc(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
