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
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
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

    // Use the admin email configured in application.properties as the sender address
    @Value("${spring.mail.username}")
    String from;

    private final JavaMailSender mailSender;
    private final FieldExtractorRegistry fieldExtractorRegistry;
    private final AiSummaryService aiSummaryService;

    @Async
    public void processAndSendNotifications(List<EmailSubscriber> subscribers,
                                            List<EntityModel> results,
                                            List<String> fields,
                                            int limit,
                                            Workspace workspace) {
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
        String htmlBody = buildHtmlTable(results, displayFields, limit, aiSummaryEnabled, aiSummaries, linkContext);
        for (EmailSubscriber subscriber : subscribers) {
            sendEmail(subscriber.getRecipientEmail(), htmlBody);
        }
    }

    private void sendEmail(String to, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("[ve-emailer] Your Notification Digest");
            helper.setText(htmlBody, true); // true = HTML
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send notification email to {}", to, e);
        }
    }

    /**
     * Convenience overload — delegates to the full implementation with no hyperlink context.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit,
                          boolean aiSummaryEnabled, String[] aiSummaries) {
        return buildHtmlTable(results, fields, limit, aiSummaryEnabled, aiSummaries, null);
    }

    /**
     * Builds a styled HTML table whose columns are the filter's field names and
     * whose rows are the Octane entities returned by the filter execution.
     * When AI Summary is enabled, it appears as the first column.
     * When {@code linkContext} is provided, hyperlink-eligible fields ({@code id},
     * {@code global_id_udf}) are rendered as clickable deep-links to the ValueEdge ticket page.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit,
                          boolean aiSummaryEnabled, String[] aiSummaries,
                          TicketLinkContext linkContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\"font-family:Arial,sans-serif;font-size:14px;\">")
          .append("<p>Here is your notification digest:</p>");

        if (results.isEmpty()) {
            sb.append("<p><em>No items matched the filter criteria.</em></p>");
        } else {
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
                    String cellValue = extractFieldValue(field, entity.getValue(field));
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

        sb.append("<p style=\"font-size:11px;color:#888;\">")
          .append("This list is limited to ").append(limit).append(" items.")
          .append("</p>");
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
