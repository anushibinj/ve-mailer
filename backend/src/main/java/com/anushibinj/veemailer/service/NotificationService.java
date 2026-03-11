package com.anushibinj.veemailer.service;

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

import com.anushibinj.veemailer.model.EmailSubscriber;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
	
	// Use the admin email configured in application.properties as the sender address
	@Value("${spring.mail.username}")
	String from;

    private final JavaMailSender mailSender;

    @Async
    public void processAndSendNotifications(List<EmailSubscriber> subscribers,
                                            List<EntityModel> results,
                                            List<String> fields,
                                            int limit) {
        String htmlBody = buildHtmlTable(results, fields, limit);
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
     * Builds a styled HTML table whose columns are the filter's field names and
     * whose rows are the Octane entities returned by the filter execution.
     */
    String buildHtmlTable(List<EntityModel> results, List<String> fields, int limit) {
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
                for (String field : fields) {
                    String cellValue = extractFieldValue(entity.getValue(field));
                    sb.append("<td style=\"padding:8px;\">")
                      .append(escapeHtml(cellValue))
                      .append("</td>");
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
     */
    private String extractFieldValue(FieldModel<?> fm) {
        if (fm == null || !fm.hasValue() || fm.getValue() == null) {
            return "";
        }
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
        if (fm instanceof MultiReferenceFieldModel mrfm) {
            return mrfm.getValue().stream()
                    .map(ref -> resolveRefName(ref))
                    .collect(Collectors.joining(", "));
        }
        if (fm instanceof ReferenceFieldModel rfm) {
            return resolveRefName(rfm.getValue());
        }
        return fm.getValue().toString();
    }

    /** Resolves the display name of a referenced EntityModel (e.g. phase, owner). */
    private String resolveRefName(EntityModel ref) {
        if (ref == null) return "";
        FieldModel<?> nameField = ref.getValue("name");
        if (nameField instanceof StringFieldModel s && s.getValue() != null) {
            return s.getValue();
        }
        String id = ref.getId();
        return id != null ? id : "";
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
}
