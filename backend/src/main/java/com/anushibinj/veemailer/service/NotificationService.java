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
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Fields that are rendered as clickable hyperlinks to the ValueEdge ticket page. */
    static final Set<String> HYPERLINK_FIELDS = Set.of("id", "global_id_udf");

    /**
     * Carries the ValueEdge connection details needed to generate ticket hyperlinks.
     * Pass {@code null} to disable hyperlink generation.
     */
    record TicketLinkContext(String serverUrl, String sharedSpaceId, String workspaceId) {}

    private final DynamicMailSenderService dynamicMailSenderService;
    private final FieldExtractorRegistry fieldExtractorRegistry;
    private final AiSummaryService aiSummaryService;
    private final MailAuditService mailAuditService;

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
        // Check if AI Summary is enabled and generate summaries
        boolean aiSummaryEnabled = fields.contains(AiSummaryService.AI_SUMMARY_FIELD);
        List<String> displayFields = fields;
        String[] aiSummaries = null;

        if (aiSummaryEnabled) {
            // Remove AI Summary pseudo-field from the Octane field list for display ordering
            displayFields = fields.stream()
                    .filter(f -> !AiSummaryService.AI_SUMMARY_FIELD.equals(f))
                    .collect(Collectors.toList());

            // Generate AI summaries for each ticket
            aiSummaries = new String[results.size()];
            for (int i = 0; i < results.size(); i++) {
                EntityModel entity = results.get(i);
                String name = extractFieldValue("name", entity.getValue("name"));
                String description = extractFieldValue("description", entity.getValue("description"));
                String ticketId = extractFieldValue("id", entity.getValue("id"));
                String comments = aiSummaryService.fetchComments(ticketId, workspace);
                aiSummaries[i] = aiSummaryService.generateSummary(name, description, comments);
            }
        }

        // Build the link context so ticket id/global_id_udf cells render as hyperlinks.
        TicketLinkContext linkContext = new TicketLinkContext(
                workspace.getRootUrl(),
                workspace.getSharedSpaceId(),
                workspace.getWorkspaceId());
        String htmlBody = buildHtmlTable(results, displayFields, limit, aiSummaryEnabled, aiSummaries, linkContext, filterTitle);
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
     * Convenience overload — delegates to the full implementation with no hyperlink context or filter title.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit,
                          boolean aiSummaryEnabled, String[] aiSummaries) {
        return buildHtmlTable(results, fields, limit, aiSummaryEnabled, aiSummaries, null, null);
    }

    /**
     * Convenience overload — delegates to the full implementation with no filter title.
     * Used by tests and callers that only need hyperlink support without a filter name in the intro.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit,
                          boolean aiSummaryEnabled, String[] aiSummaries,
                          TicketLinkContext linkContext) {
        return buildHtmlTable(results, fields, limit, aiSummaryEnabled, aiSummaries, linkContext, null);
    }

    /**
     * Builds a styled HTML table whose columns are the filter's field names and
     * whose rows are the Octane entities returned by the filter execution.
     * The intro line shows the ticket count and filter name.
     * When AI Summary is enabled, it appears as the first column.
     * When {@code linkContext} is provided, hyperlink-eligible fields ({@code id},
     * {@code global_id_udf}) are rendered as clickable deep-links to the ValueEdge ticket page.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit,
                          boolean aiSummaryEnabled, String[] aiSummaries,
                          TicketLinkContext linkContext, String filterTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\"font-family:Arial,sans-serif;font-size:14px;\">");

        String trimmedTitle = filterTitle == null ? "" : filterTitle.strip();
        int count = results.size();

        if (results.isEmpty()) {
            if (!trimmedTitle.isEmpty()) {
                sb.append("<p>No items matched the filter <strong>&quot;")
                  .append(escapeHtml(trimmedTitle)).append("&quot;</strong>.</p>");
            } else {
                sb.append("<p><em>No items matched the filter criteria.</em></p>");
            }
        } else {
            // Intro line: ticket count + filter name
            sb.append("<p>");
            if (!trimmedTitle.isEmpty()) {
                sb.append("<strong>").append(count).append(count == 1 ? " ticket" : " tickets")
                  .append("</strong> available for the filter <strong>&quot;")
                  .append(escapeHtml(trimmedTitle)).append("&quot;</strong>.");
            } else {
                sb.append("<strong>").append(count).append(count == 1 ? " ticket" : " tickets")
                  .append("</strong> in this notification.");
            }
            sb.append("</p>");

            sb.append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" ")
              .append("style=\"border-collapse:collapse;width:100%;\">");

            // Header row
            sb.append("<thead><tr style=\"background-color:#f2f2f2;\">");
            if (aiSummaryEnabled) {
                sb.append("<th style=\"text-align:left;padding:8px;\">AI Summary</th>");
            }
            for (String field : fields) {
                sb.append("<th style=\"text-align:left;padding:8px;\">")
                  .append(escapeHtml(humanise(field)))
                  .append("</th>");
            }
            sb.append("</tr></thead>");

            // Data rows
            sb.append("<tbody>");
            for (int i = 0; i < results.size(); i++) {
                EntityModel entity = results.get(i);
                String rowBg = (i % 2 == 0) ? "#ffffff" : "#f9f9f9";
                sb.append("<tr style=\"background-color:").append(rowBg).append(";\">");
                if (aiSummaryEnabled) {
                    String summary = (aiSummaries != null && i < aiSummaries.length)
                            ? aiSummaries[i] : "AI summary unavailable.";
                    // AI summary is rendered as sanitized HTML — not escaped — so anchor tags,
                    // emphasis, and other email-safe formatting display correctly.
                    sb.append("<td style=\"padding:8px;\">")
                      .append(sanitizeAiHtml(summary))
                      .append("</td>");
                }
                for (String field : fields) {
                    String cellValue = TriageSlaPolicy.TRIAGE_SLA_FIELD.equals(field)
                            ? TriageSlaPolicy.toDisplayLabel(entity)
                            : extractFieldValue(field, entity.getValue(field));
                    sb.append("<td style=\"padding:8px;\">" );
                    if (linkContext != null && HYPERLINK_FIELDS.contains(field)) {
                        // Hyperlink-eligible field: render as anchor to the VE ticket page.
                        // For global_id_udf the display text is the field's own value, but
                        // the URL always uses the internal numeric id for navigation.
                        String ticketId = extractFieldValue("id", entity.getValue("id"));
                        sb.append(buildTicketLink(linkContext, ticketId, cellValue));
                    } else {
                        sb.append(escapeHtml(cellValue));
                    }
                    sb.append("</td>");
                }
                sb.append("</tr>");
            }
            sb.append("</tbody></table>");
        }

        // Only show the "limited to N items" footer when a positive limit is in effect.
        // When limit is -1 (unlimited), the footer is omitted entirely.
        if (limit > 0) {
            sb.append("<p style=\"font-size:11px;color:#888;\">")
              .append("This list is limited to ").append(limit).append(" items.")
              .append("</p>");
        }
        sb.append("</body></html>");
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
        return "<a href=\"" + escapeHtml(href) + "\" style=\"color:#1a73e8;\">" + escapeHtml(label) + "</a>";
    }
}
