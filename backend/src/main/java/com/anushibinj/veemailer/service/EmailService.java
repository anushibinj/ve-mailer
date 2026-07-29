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
                "<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:#cbd5e1;\">" +
                "Use the one-time code below to sign in to VE Mailer. " +
                "This code will expire in 10 minutes." +
                "</p>" +
                "<div style=\"display:inline-block;background-color:#0f172a;border:1px solid #334155;" +
                "border-radius:8px;padding:16px 32px;margin:0 0 20px;\">" +
                "<span style=\"font-size:28px;font-weight:700;letter-spacing:0.15em;color:#f1f5f9;" +
                "font-family:monospace;\">" + esc(otp) + "</span>" +
                "</div>" +
                "<p style=\"margin:0;font-size:12px;color:#475569;\">" +
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
                "<p style=\"margin:0 0 16px;font-size:14px;line-height:1.6;color:#cbd5e1;\">" +
                "Hello <strong style=\"color:#f1f5f9;\">" + esc(name) + "</strong>,<br>" +
                "You have been invited to use VE Mailer. Click the button below to finish setting up your account." +
                "</p>" +
                "<div style=\"margin:24px 0;\">" +
                "<a href=\"" + esc(magicLink) + "\" " +
                "style=\"display:inline-block;background:linear-gradient(135deg,#4f46e5 0%,#7c3aed 100%);" +
                "color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;" +
                "padding:12px 28px;border-radius:8px;letter-spacing:0.02em;\">" +
                "Accept invitation &#8594;</a>" +
                "</div>" +
                "<p style=\"margin:0 0 8px;font-size:12px;color:#475569;\">" +
                "This link expires in <strong style=\"color:#94a3b8;\">" + expiresInMinutes + " minutes</strong> " +
                "and can be used only once." +
                "</p>" +
                "<p style=\"margin:0 0 16px;font-size:12px;color:#475569;\">" +
                "If the button doesn't work, copy and paste this URL into your browser:<br>" +
                "<span style=\"color:#818cf8;word-break:break-all;\">" + esc(magicLink) + "</span>" +
                "</p>" +
                "<p style=\"margin:0;font-size:12px;color:#334155;\">" +
                "Didn't expect this invitation? You can safely ignore this email.<br>" +
                "You can also request a fresh link from: " +
                "<a href=\"" + esc(frontendUrl) + "/accept-invite\" style=\"color:#818cf8;\">" +
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
                "<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:#cbd5e1;\">" +
                "A new user has completed onboarding on VE Mailer." +
                "</p>" +
                "<div style=\"background-color:#0f172a;border:1px solid #334155;border-radius:8px;" +
                "padding:16px 20px;margin:0 0 20px;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">" +
                "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;\">Name</td>" +
                "<td style=\"padding:4px 0;font-size:14px;color:#f1f5f9;\">" + esc(newUserName) + "</td></tr>" +
                "<tr><td style=\"padding:4px 16px 4px 0;font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;white-space:nowrap;\">Email</td>" +
                "<td style=\"padding:4px 0;font-size:14px;color:#f1f5f9;\">" + esc(newUserEmail) + "</td></tr>" +
                "</table>" +
                "</div>" +
                "<p style=\"margin:0;font-size:12px;color:#475569;\">" +
                "Manage users in the admin panel: " +
                "<a href=\"" + esc(frontendUrl) + "/admin\" style=\"color:#818cf8;\">" +
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

    /**
     * Wraps a content block in the standard dark-themed VE Mailer email shell.
     * All CSS is inline so it renders correctly in email clients that strip {@code <style>} blocks.
     */
    private String buildEmailShell(String title, String bodyHtml) {
        return "<!DOCTYPE html><html><head>" +
               "<meta charset=\"UTF-8\">" +
               "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
               "</head>" +
               "<body style=\"margin:0;padding:0;background-color:#0f172a;" +
               "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;\">" +
               "<div style=\"background-color:#0f172a;padding:32px 16px;\">" +
               "<div style=\"max-width:560px;margin:0 auto;background-color:#1e293b;" +
               "border-radius:12px;border:1px solid #334155;overflow:hidden;\">" +
               "<div style=\"background:linear-gradient(135deg,#4f46e5 0%,#7c3aed 100%);padding:18px 28px;\">" +
               "<span style=\"color:#ffffff;font-size:16px;font-weight:700;letter-spacing:-0.3px;\">" +
               "&#9993;&nbsp;&nbsp;VE Mailer</span>" +
               "</div>" +
               "<div style=\"padding:28px;\">" +
               "<h2 style=\"margin:0 0 20px;font-size:18px;font-weight:700;color:#f1f5f9;" +
               "letter-spacing:-0.3px;\">" + esc(title) + "</h2>" +
               bodyHtml +
               "</div>" +
               "<div style=\"background-color:#0f172a;padding:12px 28px;" +
               "border-top:1px solid #334155;text-align:center;\">" +
               "<span style=\"font-size:11px;color:#475569;\">VE Mailer &middot; Automated notification system</span>" +
               "</div>" +
               "</div></div></body></html>";
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
