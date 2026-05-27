package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.repository.PostgresAvailableCondition.EnabledIfPostgresAvailable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for MailAuditLog queries against a real PostgreSQL instance.
 * Validates that null parameters, LOWER(), and LIKE work correctly with Hibernate 6
 * on PostgreSQL — this was previously broken by "could not determine data type of parameter"
 * errors when using the (:param IS NULL OR ...) JPQL pattern.
 *
 * Requires a running PostgreSQL instance (from docker-compose).
 * Gracefully skips if Docker is unavailable.
 */
@DataJpaTest
@EnabledIfPostgresAvailable
@ActiveProfiles("postgres-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MailAuditLogRepositoryPostgresIT {

    @Autowired
    private MailAuditLogRepository repository;

    private UUID workspace1;
    private UUID workspace2;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        workspace1 = UUID.randomUUID();
        workspace2 = UUID.randomUUID();

        repository.save(MailAuditLog.builder()
                .workspaceId(workspace1)
                .workspaceTitle("Alpha Workspace")
                .recipientEmail("alice@company.com")
                .filterTitle("Critical Defects")
                .deliveryStatus(DeliveryStatus.SUCCESS)
                .sentAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .ticketCount(5)
                .build());

        repository.save(MailAuditLog.builder()
                .workspaceId(workspace1)
                .workspaceTitle("Alpha Workspace")
                .recipientEmail("bob@company.com")
                .filterTitle("High Priority Bugs")
                .deliveryStatus(DeliveryStatus.FAILED)
                .failureReason("SMTP timeout")
                .sentAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .ticketCount(3)
                .build());

        repository.save(MailAuditLog.builder()
                .workspaceId(workspace2)
                .workspaceTitle("Beta Workspace")
                .recipientEmail("alice@company.com")
                .filterTitle("All Open Defects")
                .deliveryStatus(DeliveryStatus.SUCCESS)
                .sentAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .ticketCount(12)
                .build());
    }

    @Test
    void findAll_allNullFilters_returnsAll() {
        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(null, null, null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page.getContent()).hasSize(3);
    }

    @Test
    void findAll_byWorkspaceId() {
        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(workspace1, null, null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allMatch(m -> m.getWorkspaceId().equals(workspace1));
    }

    @Test
    void findAll_byRecipientEmail() {
        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(null, "alice@company.com", null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allMatch(m -> m.getRecipientEmail().equals("alice@company.com"));
    }

    @Test
    void findAll_byFilterTitleLike_caseInsensitive() {
        // Search for "defect" should match "Critical Defects" and "All Open Defects"
        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(null, null, "defect", null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allMatch(m ->
                m.getFilterTitle().toLowerCase().contains("defect"));
    }

    @Test
    void findAll_byFilterTitleLike_partialMatch() {
        // "critical" should match only "Critical Defects"
        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(null, null, "critical", null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getFilterTitle()).isEqualTo("Critical Defects");
    }

    @Test
    void findAll_byStatus() {
        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(null, null, null, DeliveryStatus.FAILED, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getRecipientEmail()).isEqualTo("bob@company.com");
    }

    @Test
    void findAll_byDateRange() {
        Instant from = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant to = Instant.now();

        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(null, null, null, null, from, to),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        // Only the two recent entries (1h and 2h ago), not the 3-day-old one
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void findAll_combinedFilters() {
        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(workspace1, "alice@company.com", "critical",
                        DeliveryStatus.SUCCESS, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getFilterTitle()).isEqualTo("Critical Defects");
    }

    @Test
    void findAll_noMatch_returnsEmpty() {
        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(UUID.randomUUID(), null, null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void findAll_pagination_works() {
        Page<MailAuditLog> page0 = repository.findAll(
                MailAuditLogSpecs.filtered(null, null, null, null, null, null),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getTotalPages()).isEqualTo(2);

        Page<MailAuditLog> page1 = repository.findAll(
                MailAuditLogSpecs.filtered(null, null, null, null, null, null),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page1.getContent()).hasSize(1);
    }

    @Test
    void findAll_nullFilterTitle_doesNotCauseTypeError() {
        // This is the key regression test — previously caused:
        // "ERROR: could not determine data type of parameter $9"
        Page<MailAuditLog> page = repository.findAll(
                MailAuditLogSpecs.filtered(workspace1, null, null, DeliveryStatus.SUCCESS, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sentAt")));

        assertThat(page.getContent()).hasSize(1);
    }
}
