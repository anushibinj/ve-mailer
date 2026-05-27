package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Specifications for building dynamic queries against {@link MailAuditLog}.
 * Using Criteria API avoids the Hibernate 6 / PostgreSQL issue where null parameters
 * in JPQL cause "could not determine data type of parameter" errors.
 */
public final class MailAuditLogSpecs {

    private MailAuditLogSpecs() {
    }

    public static Specification<MailAuditLog> withWorkspaceId(UUID workspaceId) {
        if (workspaceId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
    }

    public static Specification<MailAuditLog> withRecipientEmail(String recipientEmail) {
        if (recipientEmail == null) return null;
        return (root, query, cb) -> cb.equal(root.get("recipientEmail"), recipientEmail);
    }

    public static Specification<MailAuditLog> withFilterTitleLike(String filterTitle) {
        if (filterTitle == null) return null;
        String pattern = "%" + filterTitle.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("filterTitle")), pattern);
    }

    public static Specification<MailAuditLog> withStatus(DeliveryStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("deliveryStatus"), status);
    }

    public static Specification<MailAuditLog> sentAfter(Instant from) {
        if (from == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("sentAt"), from);
    }

    public static Specification<MailAuditLog> sentBefore(Instant to) {
        if (to == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("sentAt"), to);
    }

    /**
     * Combines all filter criteria. Null specifications are ignored by {@code Specification.where()}.
     */
    public static Specification<MailAuditLog> filtered(UUID workspaceId, String recipientEmail,
                                                       String filterTitle, DeliveryStatus status,
                                                       Instant from, Instant to) {
        return Specification.where(withWorkspaceId(workspaceId))
                .and(withRecipientEmail(recipientEmail))
                .and(withFilterTitleLike(filterTitle))
                .and(withStatus(status))
                .and(sentAfter(from))
                .and(sentBefore(to));
    }
}
