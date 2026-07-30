package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.service.extractor.FieldExtractorRegistry;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import com.hpe.adm.nga.sdk.model.BooleanFieldModel;
import com.hpe.adm.nga.sdk.model.DateFieldModel;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.FieldModel;
import com.hpe.adm.nga.sdk.model.FloatFieldModel;
import com.hpe.adm.nga.sdk.model.LongFieldModel;
import com.hpe.adm.nga.sdk.model.MultiReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Fields that are rendered as clickable hyperlinks to the ValueEdge ticket page. */
    static final Set<String> HYPERLINK_FIELDS = Set.of("id", "global_id_udf");
    private static final Map<String, String> PHASE_CATEGORY_BY_KEY = Map.ofEntries(
            // To Do
            Map.entry("new", "todo"),
            Map.entry("ready", "todo"),
            Map.entry("planned", "todo"),
            // In Progress
            Map.entry("in progress", "in_progress"),
            Map.entry("code review", "in_progress"),
            Map.entry("in testing", "in_progress"),
            Map.entry("pending support", "in_progress"),
            Map.entry("awaiting decision", "in_progress"),
            // Done
            Map.entry("implemented", "done"),
            Map.entry("fixed", "done"),
            Map.entry("tested", "done"),
            Map.entry("done", "done"),
            Map.entry("completed", "done"),
            // Cancelled
            Map.entry("cancelled", "cancelled"),
            Map.entry("deferred", "cancelled"),
            // Rejected
            Map.entry("proposed rejected", "rejected"),
            Map.entry("rejected", "rejected"),
            Map.entry("duplicate", "rejected")
    );

    /**
     * Carries the ValueEdge connection details needed to generate ticket hyperlinks.
     * Pass {@code null} to disable hyperlink generation.
     */
    record TicketLinkContext(String serverUrl, String sharedSpaceId, String workspaceId) {}

    private final DynamicMailSenderService dynamicMailSenderService;
    private final FieldExtractorRegistry fieldExtractorRegistry;
    private final AiSummaryService aiSummaryService;
    private final MailAuditService mailAuditService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Builds a standardised email subject including the ticket count.
     * Format: "[ve-mailer] 5 tickets – {filterTitle}"
     * Falls back to "[ve-mailer] {count} tickets" when the title is null or blank.
     */
    static String buildMailSubject(String filterTitle, int count) {
        String trimmed = filterTitle == null ? "" : filterTitle.strip();
        String countPart = count + " ticket" + (count == 1 ? "" : "s");
        return trimmed.isEmpty()
                ? "[ve-mailer] " + countPart
                : "[ve-mailer] " + countPart + " \u2013 " + trimmed;
    }

    @Async
    public void processAndSendNotifications(List<EmailSubscriber> subscribers,
                                            List<EntityModel> results,
                                            List<String> fields,
                                            int limit,
                                            Workspace workspace,
                                            String filterTitle) {
        if (results.isEmpty()) {
            log.info("Skipping notification emails for workspace {} filter '{}' because no tickets matched",
                    workspace.getId(), filterTitle);
            recordNoTicketAuditEntries(subscribers, workspace, filterTitle);
            return;
        }

        // Check if AI Summary is enabled and generate summaries
        boolean aiSummaryEnabled = fields.contains(AiSummaryService.AI_SUMMARY_FIELD);
        String[] aiSummaries = null;

        if (aiSummaryEnabled) {
            // Generate AI summaries for each ticket
            aiSummaries = new String[results.size()];
            for (int i = 0; i < results.size(); i++) {
                EntityModel entity = results.get(i);
                String name = extractFieldValue("name", entity.getValue("name"));
                String description = extractFieldValue("description", entity.getValue("description"));
                String ticketId = extractFieldValue("id", entity.getValue("id"));
                String phaseAgeStr = extractFieldValue("phase_age", entity.getValue("phase_age"));
                Integer phaseAge = parsePhaseAge(phaseAgeStr);
                String comments = aiSummaryService.fetchComments(ticketId, workspace);
                aiSummaries[i] = aiSummaryService.generateSummary(name, description, comments, phaseAge);
            }
        }

        // Build the link context so ticket id/global_id_udf cells render as hyperlinks.
        TicketLinkContext linkContext = new TicketLinkContext(
                workspace.getRootUrl(),
                workspace.getSharedSpaceId(),
                workspace.getWorkspaceId());
        String workspaceUrl = frontendUrl.stripTrailing() + "/workspace/" + workspace.getId();
        String htmlBody = buildHtmlTable(results, fields, limit, aiSummaryEnabled, aiSummaries, linkContext, filterTitle, workspaceUrl);
        String subject = buildMailSubject(filterTitle, results.size());
        // Each subscriber is handled independently so one failure cannot affect the others.
        for (EmailSubscriber subscriber : subscribers) {
            if (subscriber.getGroup() != null) {
                // Group subscription: one email thread to all current group members.
                sendGroupEmail(subscriber, htmlBody, subject, workspace, filterTitle, results.size());
            } else {
                // Individual subscription: one email per person.
                String recipientEmail = subscriber.getRecipientEmail();
                if (recipientEmail == null || recipientEmail.isBlank()) {
                    log.warn("Skipping subscriber {} — no recipient email", subscriber.getId());
                    continue;
                }
                long start = System.currentTimeMillis();
                try {
                    sendEmail(recipientEmail, htmlBody, subject);
                    long duration = System.currentTimeMillis() - start;
                    mailAuditService.recordSuccess(
                            workspace.getId(), workspace.getTitle(),
                            recipientEmail,
                            subscriber.getFilter() != null ? subscriber.getFilter().getId() : null,
                            filterTitle, subscriber.getId(), null,
                            subject, results.size(), duration);
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    log.error("Failed to send notification email to {}", recipientEmail, e);
                    mailAuditService.recordFailure(
                            workspace.getId(), workspace.getTitle(),
                            recipientEmail,
                            subscriber.getFilter() != null ? subscriber.getFilter().getId() : null,
                            filterTitle, subscriber.getId(), null,
                            subject, results.size(), duration,
                            e.getMessage());
                }
            }
        }
    }

    private void sendEmail(String to, String htmlBody, String subject) throws MessagingException {
        Session session = dynamicMailSenderService.getSession();
        String from = dynamicMailSenderService.getFromAddress();
        MimeMessage message = new MimeMessage(session);
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true); // true = HTML
        message.saveChanges();
        dynamicMailSenderService.send(message);
    }

    /**
     * Sends one email with all group members on the To line and records an audit entry per member.
     * The single send failure is caught here so other subscribers in the batch are not affected.
     */
    private void sendGroupEmail(EmailSubscriber groupSub, String htmlBody, String subject,
                                Workspace workspace, String filterTitle, int ticketCount) {
        Set<String> memberEmailSet = groupSub.getGroup().getMemberEmails();
        List<String> validEmails = memberEmailSet == null ? List.of() : memberEmailSet.stream()
                .filter(e -> e != null && !e.isBlank())
                .collect(Collectors.toList());

        if (validEmails.isEmpty()) {
            log.warn("Group subscription {} ({}) has no members — skipping",
                    groupSub.getId(), groupSub.getGroup().getName());
            return;
        }

        long start = System.currentTimeMillis();
        try {
            sendEmailToMultiple(validEmails, htmlBody, subject);
            long duration = System.currentTimeMillis() - start;
            // One audit success entry per member for traceability
            for (String email : validEmails) {
                mailAuditService.recordSuccess(
                        workspace.getId(), workspace.getTitle(), email,
                        groupSub.getFilter() != null ? groupSub.getFilter().getId() : null,
                        filterTitle, groupSub.getId(), null,
                        subject, ticketCount, duration);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Failed to send group email for group {} ({})",
                    groupSub.getGroup().getName(), groupSub.getId(), e);
            for (String email : validEmails) {
                mailAuditService.recordFailure(
                        workspace.getId(), workspace.getTitle(), email,
                        groupSub.getFilter() != null ? groupSub.getFilter().getId() : null,
                        filterTitle, groupSub.getId(), null,
                        subject, ticketCount, duration, e.getMessage());
            }
        }
    }

    /** Sends one email to multiple recipients on the same To line. */
    private void sendEmailToMultiple(List<String> recipients, String htmlBody, String subject)
            throws MessagingException {
        Session session = dynamicMailSenderService.getSession();
        String from = dynamicMailSenderService.getFromAddress();
        MimeMessage message = new MimeMessage(session);
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        message.saveChanges();
        dynamicMailSenderService.send(message);
    }

    /**
     * Records one audit entry per intended recipient when no tickets matched a filter.
     */
    private void recordNoTicketAuditEntries(List<EmailSubscriber> subscribers, Workspace workspace, String filterTitle) {
        String subject = buildMailSubject(filterTitle, 0);
        for (EmailSubscriber subscriber : subscribers) {
            if (subscriber.getGroup() != null) {
                Set<String> memberEmailSet = subscriber.getGroup().getMemberEmails();
                List<String> validEmails = memberEmailSet == null ? List.of() : memberEmailSet.stream()
                        .filter(e -> e != null && !e.isBlank())
                        .collect(Collectors.toList());
                for (String email : validEmails) {
                    mailAuditService.recordSkippedNoTickets(
                            workspace.getId(), workspace.getTitle(), email,
                            subscriber.getFilter() != null ? subscriber.getFilter().getId() : null,
                            filterTitle, subscriber.getId(), null, subject);
                }
                continue;
            }

            String recipientEmail = subscriber.getRecipientEmail();
            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.warn("Skipping no-ticket audit for subscriber {} — no recipient email", subscriber.getId());
                continue;
            }
            mailAuditService.recordSkippedNoTickets(
                    workspace.getId(), workspace.getTitle(), recipientEmail,
                    subscriber.getFilter() != null ? subscriber.getFilter().getId() : null,
                    filterTitle, subscriber.getId(), null, subject);
        }
    }

    /**
     * Convenience overload — delegates to the full implementation with no hyperlink context or filter title.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit,
                          boolean aiSummaryEnabled, String[] aiSummaries) {
        return buildHtmlTable(results, fields, limit, aiSummaryEnabled, aiSummaries, null, null, null);
    }

    /**
     * Convenience overload — delegates to the full implementation with no filter title or workspace URL.
     * Used by tests and callers that only need hyperlink support without a filter name in the intro.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit,
                          boolean aiSummaryEnabled, String[] aiSummaries,
                          TicketLinkContext linkContext) {
        return buildHtmlTable(results, fields, limit, aiSummaryEnabled, aiSummaries, linkContext, null, null);
    }

    /**
     * Convenience overload — delegates to the full implementation with no workspace URL.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit,
                          boolean aiSummaryEnabled, String[] aiSummaries,
                          TicketLinkContext linkContext, String filterTitle) {
        return buildHtmlTable(results, fields, limit, aiSummaryEnabled, aiSummaries, linkContext, filterTitle, null);
    }

    /**
     * Builds a light-themed, professional HTML email body with the given ticket data.
     *
     * <p>Light mode is used by default — Outlook and other clients will apply their own
     * dark mode conversion if the user has it enabled, which produces correct results.
     * Attempting to force dark mode in email HTML leads to colour-inversion issues in
     * Outlook dark mode. All CSS is inline; table-based layout with {@code bgcolor}
     * attributes ensures backgrounds survive across all major email clients.
     *
     * <p>Layout rule: the data grid uses {@code <thead>}; the empty-state path produces no
     * {@code <thead>}, which the empty-state test asserts on.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit,
                          boolean aiSummaryEnabled, String[] aiSummaries,
                          TicketLinkContext linkContext, String filterTitle, String workspaceUrl) {
        List<String> orderedFields = new ArrayList<>(fields);
        if (aiSummaryEnabled && !orderedFields.contains(AiSummaryService.AI_SUMMARY_FIELD)) {
            orderedFields.add(0, AiSummaryService.AI_SUMMARY_FIELD);
        }

        String trimmedTitle = filterTitle == null ? "" : filterTitle.strip();
        int count = results.size();

        StringBuilder sb = new StringBuilder();

        // ── Email shell ──────────────────────────────────────────────────────
        sb.append("<!DOCTYPE html><html lang=\"en\"><head>")
          .append("<meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
          .append("</head>")
          .append("<body style=\"margin:0;padding:0;background-color:#f1f5f9;")
          .append("font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;\">")
          // Outer wrapper — bgcolor on <td> ensures background survives in all Outlook versions
          .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
          .append("bgcolor=\"#f1f5f9\" style=\"background-color:#f1f5f9;\"><tr>")
          .append("<td align=\"center\" bgcolor=\"#f1f5f9\" ")
          .append("style=\"padding:32px 16px;background-color:#f1f5f9;\">")
          // Card — full-width so it fills the reading pane
          .append("<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" bgcolor=\"#ffffff\" ")
          .append("style=\"width:100%;background-color:#ffffff;")
          .append("border-radius:12px;border:1px solid #e2e8f0;\">")
          // Gradient header — bgcolor is a flat indigo fallback for Outlook (MSO ignores CSS gradients)
          .append("<tr><td bgcolor=\"#4f46e5\" ")
          .append("style=\"background:linear-gradient(135deg,#4f46e5 0%,#7c3aed 100%);")
          .append("padding:18px 28px;border-radius:12px 12px 0 0;\">")
          .append("<span style=\"color:#ffffff;font-size:16px;font-weight:700;letter-spacing:-0.3px;\">")
          .append("&#9993;&nbsp;&nbsp;VE Mailer</span></td></tr>");

        // ── Content row ──────────────────────────────────────────────────────
        sb.append("<tr><td bgcolor=\"#ffffff\" ")
          .append("style=\"background-color:#ffffff;padding:24px 28px 20px;\">");

        // Intro paragraph
        sb.append("<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:#334155;\">");
        if (results.isEmpty()) {
            if (!trimmedTitle.isEmpty()) {
                sb.append("No items matched the filter <strong style=\"color:#1e293b;\">&quot;")
                  .append(escapeHtml(trimmedTitle)).append("&quot;</strong>.");
            } else {
                sb.append("<em style=\"color:#64748b;\">No items matched the filter criteria.</em>");
            }
        } else {
            sb.append("<strong style=\"color:#1e293b;\">").append(count)
              .append(count == 1 ? " ticket" : " tickets").append("</strong>");
            if (!trimmedTitle.isEmpty()) {
                sb.append(" matched the filter <strong style=\"color:#1e293b;\">&quot;")
                  .append(escapeHtml(trimmedTitle)).append("&quot;</strong>.");
            } else {
                sb.append(" in this notification.");
            }
            if (workspaceUrl != null) {
                sb.append("&nbsp;&nbsp;<a href=\"").append(escapeHtml(workspaceUrl))
                  .append("\" style=\"color:#4f46e5;text-decoration:none;font-size:12px;white-space:nowrap;\">")
                  .append("View subscriptions &#8594;</a>");
            }
        }
        sb.append("</p>");

        // Data grid — only present when there are results.
        // The empty-state test asserts absence of <thead>, which only appears here.
        if (!results.isEmpty()) {
            sb.append("<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" ")
              .append("style=\"border-collapse:collapse;width:100%;border:1px solid #e2e8f0;\">")
              .append("<thead><tr bgcolor=\"#f8fafc\" style=\"background-color:#f8fafc;\">");
            for (String field : orderedFields) {
                String label = AiSummaryService.AI_SUMMARY_FIELD.equals(field) ? "AI Summary" : humanise(field);
                sb.append("<th style=\"padding:10px 12px;text-align:left;font-size:11px;font-weight:600;")
                  .append("text-transform:uppercase;letter-spacing:0.05em;color:#64748b;")
                  .append("border:1px solid #e2e8f0;\">")
                  .append(escapeHtml(label))
                  .append("</th>");
            }
            sb.append("</tr></thead><tbody>");

            for (int i = 0; i < results.size(); i++) {
                EntityModel entity = results.get(i);
                String rowBg = (i % 2 == 0) ? "#ffffff" : "#f8fafc";
                sb.append("<tr bgcolor=\"").append(rowBg)
                  .append("\" style=\"background-color:").append(rowBg).append(";\">");
                for (String field : orderedFields) {
                    sb.append("<td style=\"padding:10px 12px;font-size:13px;color:#1e293b;border:1px solid #e2e8f0;\">");
                    if (AiSummaryService.AI_SUMMARY_FIELD.equals(field)) {
                        String summary = (aiSummaries != null && i < aiSummaries.length)
                                ? aiSummaries[i] : "AI summary unavailable.";
                        // AI summary is rendered as sanitized HTML — not escaped — so anchor tags,
                        // emphasis, and other email-safe formatting display correctly.
                        sb.append(sanitizeAiHtml(summary));
                    } else {
                        String cellValue = TriageSlaPolicy.TRIAGE_SLA_FIELD.equals(field)
                                ? TriageSlaPolicy.toDisplayLabel(entity)
                                : extractFieldValue(field, entity.getValue(field));
                        if ("phase".equals(field)) {
                            sb.append(buildPhaseBadgeHtml(cellValue));
                        } else if (linkContext != null && HYPERLINK_FIELDS.contains(field)) {
                            // Hyperlink-eligible field: render as anchor to the VE ticket page.
                            String ticketId = extractFieldValue("id", entity.getValue("id"));
                            sb.append(buildTicketLink(linkContext, ticketId, cellValue));
                        } else {
                            sb.append(escapeHtml(cellValue));
                        }
                    }
                    sb.append("</td>");
                }
                sb.append("</tr>");
            }
            sb.append("</tbody></table>");
        }

        // Limit notice (shown when a positive cap is in effect; -1 = unlimited)
        if (limit > 0) {
            sb.append("<p style=\"margin:12px 0 0;font-size:11px;color:#94a3b8;\">")
              .append("This list is limited to ").append(limit).append(" items.")
              .append("</p>");
        }
        sb.append("</td></tr>"); // end content row

        // ── Workspace footer row ──────────────────────────────────────────────
        if (workspaceUrl != null) {
            sb.append("<tr><td bgcolor=\"#f8fafc\" style=\"background-color:#f8fafc;")
              .append("border-top:1px solid #e2e8f0;padding:16px 28px;\">")
              .append("<a href=\"").append(escapeHtml(workspaceUrl))
              .append("\" style=\"color:#4f46e5;text-decoration:none;font-weight:600;font-size:13px;\">")
              .append("&#8599; Manage your subscriptions in VE Mailer</a>")
              .append("<p style=\"margin:6px 0 0;font-size:11px;color:#94a3b8;\">")
              .append("You received this email because you have an active subscription. ")
              .append("Visit the link above to adjust or disable notifications.")
              .append("</p></td></tr>");
        }

        // ── Bottom strip row ─────────────────────────────────────────────────
        sb.append("<tr><td bgcolor=\"#f1f5f9\" style=\"background-color:#f1f5f9;")
          .append("padding:12px 28px;border-top:1px solid #e2e8f0;text-align:center;\">")
          .append("<span style=\"font-size:11px;color:#94a3b8;\">")
          .append("VE Mailer &middot; Automated notification system</span>")
          .append("</td></tr>")
          .append("</table>")         // end card table
          .append("</td></tr></table>") // end outer wrapper table
          .append("</body></html>");

        return sb.toString();
    }

    /**
     * Extracts a display-friendly string from any FieldModel type.
     *
     * <p>For reference fields, delegates to the {@link FieldExtractorRegistry}
     * so that field-specific sub-field preferences (e.g. {@code owner.full_name}
     * vs {@code phase.name}) are applied automatically.
     *
     * @param fieldName the Octane field name (used to look up the right extractor)
     * @param fm        the raw field model (may be {@code null})
     */
    private String extractFieldValue(String fieldName, FieldModel<?> fm) {
        if (fm == null || !fm.hasValue() || fm.getValue() == null) {
            return "";
        }
        // Scalar types — no registry lookup needed
        if (fm instanceof StringFieldModel sfm) {
            return sfm.getValue() != null ? sfm.getValue() : "";
        }
        if (fm instanceof LongFieldModel lfm) {
            return String.valueOf(lfm.getValue());
        }
        if (fm instanceof FloatFieldModel ffm) {
            return String.valueOf(ffm.getValue());
        }
        if (fm instanceof BooleanFieldModel bfm) {
            return String.valueOf(bfm.getValue());
        }
        if (fm instanceof DateFieldModel dfm) {
            return dfm.getValue() != null ? dfm.getValue().toString() : "";
        }
        // Multi-reference: resolve each item using the registry, then join
        if (fm instanceof MultiReferenceFieldModel mrfm) {
            return mrfm.getValue().stream()
                    .map(ref -> resolveRefName(fieldName, ref))
                    .collect(Collectors.joining(", "));
        }
        // Single reference: delegate to field-specific extractor
        if (fm instanceof ReferenceFieldModel) {
            return fieldExtractorRegistry.forField(fieldName).extract(fm);
        }
        return fm.getValue().toString();
    }

    /**
     * Parses the display string produced by {@link #extractFieldValue} for the
     * {@code phase_age} field into an Integer, tolerating blank/unparseable values.
     */
    private Integer parsePhaseAge(String phaseAgeStr) {
        if (phaseAgeStr == null || phaseAgeStr.isBlank()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(phaseAgeStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves the display name of one entity inside a multi-reference field.
     * Uses the same registry lookup as single references.
     */
    private String resolveRefName(String fieldName, EntityModel ref) {
        if (ref == null) return "";
        // Wrap in a synthetic ReferenceFieldModel so the extractor can work uniformly
        ReferenceFieldModel synthetic = new ReferenceFieldModel(fieldName, ref);
        return fieldExtractorRegistry.forField(fieldName).extract(synthetic);
    }

    /** Converts an Octane field name like "story_points" → "Story Points". */
    private String humanise(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) return fieldName;
        if (TriageSlaPolicy.TRIAGE_SLA_FIELD.equals(fieldName)) return TriageSlaPolicy.TRIAGE_SLA_FIELD;
        return java.util.Arrays.stream(fieldName.split("_"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    /** Minimal HTML escaping to prevent broken markup in cell values. */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /**
     * Sanitizes AI-generated HTML before injecting it into the email body.
     * Allows a safe subset of email-friendly tags (links, emphasis, lists) and
     * strips dangerous elements (script, iframe, event attributes, etc.).
     *
     * <p>jsoup's {@link Safelist#basic()} permits: a (href with http/https/mailto),
     * b, blockquote, br, cite, code, em, i, li, ol, p, small, span, strong, ul, etc.
     */
    String sanitizeAiHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        return Jsoup.clean(html, Safelist.basic());
    }

    private String buildPhaseBadgeHtml(String phase) {
        String text = phase == null ? "" : phase.strip();
        if (text.isEmpty()) {
            return "";
        }

        String category = PHASE_CATEGORY_BY_KEY.get(normalizePhaseKey(text));
        String palette = switch (category == null ? "unknown" : category) {
            case "todo" -> "background-color:#e0f2fe;color:#075985;border:1px solid #bae6fd;";
            case "in_progress" -> "background-color:#ffedd5;color:#9a3412;border:1px solid #fed7aa;";
            case "done" -> "background-color:#dcfce7;color:#166534;border:1px solid #bbf7d0;";
            case "cancelled" -> "background-color:#f1f5f9;color:#334155;border:1px solid #cbd5e1;";
            case "rejected" -> "background-color:#ffe4e6;color:#9f1239;border:1px solid #fecdd3;";
            default -> "background-color:#f1f5f9;color:#334155;border:1px solid #e2e8f0;";
        };

        return "<span style=\"display:inline-block;padding:2px 10px;border-radius:9999px;"
                + "font-size:11px;line-height:1.35;font-weight:600;white-space:nowrap;" + palette + "\">"
                + escapeHtml(text)
                + "</span>";
    }

    private String normalizePhaseKey(String phase) {
        return phase.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .strip();
    }

    /**
     * Builds an HTML anchor pointing to a ValueEdge ticket page.
     *
     * @param ctx      VE connection context (server URL, shared-space ID, workspace ID)
     * @param ticketId the internal numeric Octane ticket ID used in the navigation URL
     * @param label    the display text for the anchor (HTML-escaped before insertion)
     * @return an {@code <a href="...">label</a>} string, or the escaped label if ticketId is blank
     */
    private String buildTicketLink(TicketLinkContext ctx, String ticketId, String label) {
        if (ticketId == null || ticketId.isBlank()) {
            return escapeHtml(label);
        }
        // The fragment (#/entity-navigation...) is client-side routing; the & inside it
        // must be escaped to &amp; when placed inside an HTML href attribute.
        String href = ctx.serverUrl()
                + "/ui/?p=" + ctx.sharedSpaceId()
                + "/" + ctx.workspaceId()
                + "#/entity-navigation?entityType=work_item&id="
                + ticketId;
        return "<a href=\"" + escapeHtml(href) + "\" style=\"color:#4f46e5;\">" + escapeHtml(label) + "</a>";
    }
}
