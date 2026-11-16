package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.JobRunStatus;
import com.anushibinj.veemailer.model.JobTriggerType;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.MailAuditLogRepository;
import com.anushibinj.veemailer.repository.ScheduledJobRunRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.anushibinj.veemailer.service.FilterService;
import com.anushibinj.veemailer.service.DynamicMailSenderService;
import com.anushibinj.veemailer.service.PollingService;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end test of the resilient-digest-job pipeline (PollingService/ScheduledJobRunService/
 * ScheduledJobExecutor/MailAuditService) against a real PostgreSQL instance via Testcontainers,
 * with real Flyway migrations and real Spring transaction management.
 *
 * <p>This exists specifically because two production bugs in this pipeline were invisible to the
 * unit tests (which mock the repository layer) and only surfaced against a real Hibernate/Postgres
 * stack:
 * <ul>
 *   <li>{@code @Modifying} queries in {@code ScheduledJobRunRepository} run non-transactionally
 *       unless the calling service method opens a transaction — a mocked repository can't tell
 *       the difference, but a real one throws {@code TransactionRequiredException}.</li>
 *   <li>An immutable {@code List.of(...)} passed into the {@code subscriberIds} element collection
 *       gets reused as Hibernate's {@code PersistentBag} backing store; a mocked {@code save()}
 *       never exercises the merge/clear() path that throws {@code UnsupportedOperationException}
 *       on a real session.</li>
 * </ul>
 *
 * <p>The Octane fetch ({@link FilterService}) and SMTP send ({@link DynamicMailSenderService}) are
 * mocked — those are external systems, not what this test is validating — everything else
 * (job-run gates, retry classification, audit upserts, the partial unique index) is real.
 *
 * <p>Requires a running Docker daemon; gracefully skipped otherwise. Run with:
 * {@code mvn verify -Ptestcontainers}.
 */
@SpringBootTest
@Testcontainers
@DockerAvailableCondition.EnabledIfDockerAvailable
class ScheduledJobFlowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vemailer_it")
            .withUsername("vemailer")
            .withPassword("vemailer");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        // Real Flyway migrations manage the schema — Hibernate must not also try to (create-drop
        // is the test-classpath default), or the partial unique index this pipeline depends on
        // would never actually be exercised.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private PollingService pollingService;
    @Autowired
    private ScheduledJobRunService scheduledJobRunService;
    @Autowired
    private ScheduledJobExecutor scheduledJobExecutor;
    @Autowired
    private ScheduledJobRunRepository scheduledJobRunRepository;
    @Autowired
    private MailAuditLogRepository mailAuditLogRepository;
    @Autowired
    private EmailSubscriberRepository emailSubscriberRepository;
    @Autowired
    private FilterRepository filterRepository;
    @Autowired
    private WorkspaceRepository workspaceRepository;

    @MockBean
    private FilterService filterService;
    @MockBean
    private DynamicMailSenderService dynamicMailSenderService;

    private Workspace workspace;
    private Filter filter;
    private EmailSubscriber subscriber;

    @BeforeEach
    void setUp() throws Exception {
        mailAuditLogRepository.deleteAll();
        scheduledJobRunRepository.deleteAll();
        emailSubscriberRepository.deleteAll();
        filterRepository.deleteAll();
        workspaceRepository.deleteAll();

        workspace = workspaceRepository.save(Workspace.builder()
                .title("IT Workspace")
                .workspaceShortcode("ITWS")
                .sharedSpaceId("1001")
                .workspaceId("2001")
                .clientId("client-id")
                .clientKey("client-key")
                .rootUrl("https://ve.example.com")
                .status(WorkspaceStatus.ENABLED)
                .build());

        filter = filterRepository.save(Filter.builder()
                .title("Open Defects")
                .entityType("defect")
                .fields("[\"name\"]")
                .criteria("[]")
                .isPublic(true)
                .workspace(workspace)
                .build());

        subscriber = emailSubscriberRepository.save(EmailSubscriber.builder()
                .filter(filter)
                .workspace(workspace)
                .recipientEmail("subscriber@company.com")
                .scheduleType(ScheduleType.DAILY)
                .scheduledHours(List.of(9))
                .status(Status.ACTIVE)
                .build());

        when(dynamicMailSenderService.getSession()).thenReturn(jakarta.mail.Session.getInstance(new Properties()));
        when(dynamicMailSenderService.getFromAddress()).thenReturn("noreply@test.com");
        doNothing().when(dynamicMailSenderService).send(any(MimeMessage.class));
    }

    /**
     * Reproduces the exact "Run now" crash: an immutable subscriberIds list surviving claim() ->
     * beginDispatch() -> dispatch() -> markSucceeded(), each a real transactional save/merge
     * against Postgres. Before the fix, markSucceeded's save() on the reloaded detached entity
     * threw UnsupportedOperationException; before the @Transactional fix, claim() itself threw
     * TransactionRequiredException.
     */
    @Test
    void manualRunNow_endToEnd_persistsSucceededRunAndAuditRow() {
        EntityModel ticket = new EntityModel(Set.of(new StringFieldModel("name", "Fix login bug")));
        when(filterService.getFilterFields(filter.getId())).thenReturn(List.of("name"));
        when(filterService.executeFilter(filter.getId(), workspace.getId())).thenReturn(List.of(ticket));
        when(filterService.getQueryLimit()).thenReturn(25);

        pollingService.runNow(subscriber);

        List<ScheduledJobRun> runs = scheduledJobRunRepository.findAll();
        assertThat(runs).hasSize(1);
        ScheduledJobRun run = runs.get(0);
        assertThat(run.getTriggerType()).isEqualTo(JobTriggerType.MANUAL);
        assertThat(run.getStatus()).isEqualTo(JobRunStatus.SUCCEEDED);
        assertThat(run.getMaxAttempts()).isEqualTo(1);
        assertThat(run.getSubscriberIds()).containsExactly(subscriber.getId());
        assertThat(run.getDispatchStartedAt()).isNotNull();
        assertThat(run.getDispatchCompletedAt()).isNotNull();

        List<MailAuditLog> auditRows = mailAuditLogRepository.findAll();
        assertThat(auditRows).hasSize(1);
        MailAuditLog auditRow = auditRows.get(0);
        assertThat(auditRow.getJobRunId()).isEqualTo(run.getId());
        assertThat(auditRow.getDeliveryStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(auditRow.getRecipientEmail()).isEqualTo("subscriber@company.com");

        verify(dynamicMailSenderService).send(any(MimeMessage.class));
    }

    /**
     * A transient fetch failure records a RETRYING row, then a successful retry upserts that same
     * row to SUCCESS in place — verified against the real partial unique index
     * (job_run_id, subscription_id, recipient_email), which is exactly what prevents a duplicate
     * audit row (and, by the same key, a duplicate send) across retry attempts.
     */
    @Test
    void scheduledRun_transientFailureThenRetrySuccess_upsertsAuditRowInPlace() {
        ScheduledJobRun run = scheduledJobRunService.createScheduledRun(
                workspace.getId(), filter.getId(), Instant.now(), List.of(subscriber.getId()), 4).orElseThrow();

        when(filterService.getFilterFields(filter.getId()))
                .thenThrow(new RuntimeException("connection reset"))
                .thenReturn(List.of("name"));
        EntityModel ticket = new EntityModel(Set.of(new StringFieldModel("name", "Fix login bug")));
        when(filterService.executeFilter(filter.getId(), workspace.getId())).thenReturn(List.of(ticket));
        when(filterService.getQueryLimit()).thenReturn(25);

        // Attempt 1: transient failure.
        scheduledJobExecutor.execute(run.getId());

        ScheduledJobRun afterFirstAttempt = scheduledJobRunRepository.findById(run.getId()).orElseThrow();
        assertThat(afterFirstAttempt.getStatus()).isEqualTo(JobRunStatus.AWAITING_RETRY);
        assertThat(afterFirstAttempt.getAttemptCount()).isEqualTo(1);
        assertThat(afterFirstAttempt.getNextRetryAt()).isNotNull();

        List<MailAuditLog> afterFirstAttemptAudit = mailAuditLogRepository.findAll();
        assertThat(afterFirstAttemptAudit).hasSize(1);
        MailAuditLog retryingRow = afterFirstAttemptAudit.get(0);
        UUID auditRowId = retryingRow.getId();
        assertThat(retryingRow.getDeliveryStatus()).isEqualTo(DeliveryStatus.RETRYING);
        assertThat(retryingRow.getFailureReason()).isEqualTo("Failed - Retried 1 time. Next retry in 15 minutes.");

        // Attempt 2 (simulating the retry driver's next tick): fetch now succeeds.
        scheduledJobExecutor.execute(run.getId());

        ScheduledJobRun afterRetry = scheduledJobRunRepository.findById(run.getId()).orElseThrow();
        assertThat(afterRetry.getStatus()).isEqualTo(JobRunStatus.SUCCEEDED);
        assertThat(afterRetry.getAttemptCount()).isEqualTo(2);

        List<MailAuditLog> afterRetryAudit = mailAuditLogRepository.findAll();
        assertThat(afterRetryAudit).hasSize(1); // upserted in place, not a second row
        MailAuditLog successRow = afterRetryAudit.get(0);
        assertThat(successRow.getId()).isEqualTo(auditRowId);
        assertThat(successRow.getDeliveryStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(successRow.getFailureReason()).isNull();

        verify(dynamicMailSenderService).send(any(MimeMessage.class));
    }

    /**
     * The creation gate against the real DB unique constraint on job_key: a duplicate tick for the
     * same (workspace, filter, slot) must never create a second job run row.
     */
    @Test
    void duplicateJobKey_realUniqueConstraint_secondCreateReturnsEmpty() {
        Instant slotAt = Instant.now();

        Optional<ScheduledJobRun> first = scheduledJobRunService.createScheduledRun(
                workspace.getId(), filter.getId(), slotAt, List.of(subscriber.getId()), 4);
        Optional<ScheduledJobRun> second = scheduledJobRunService.createScheduledRun(
                workspace.getId(), filter.getId(), slotAt, List.of(subscriber.getId()), 4);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(scheduledJobRunRepository.findAll()).hasSize(1);
    }
}
