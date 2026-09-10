package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.JobRunStatus;
import com.anushibinj.veemailer.model.JobTriggerType;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import com.anushibinj.veemailer.repository.ScheduledJobRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledJobRunServiceTest {

    @Mock
    private ScheduledJobRunRepository repository;

    private Clock clock;
    private ScheduledJobRunService service;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID filterId = UUID.randomUUID();
    private final Instant fixedNow = Instant.parse("2026-01-01T09:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        service = new ScheduledJobRunService(repository, clock);
        ReflectionTestUtils.setField(service, "staleClaimMinutes", 30L);
    }

    // ── creation gate ────────────────────────────────────────────────────────

    @Test
    void createScheduledRun_savesWithExpectedJobKeyAndStatus() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<ScheduledJobRun> result = service.createScheduledRun(
                workspaceId, filterId, fixedNow, List.of(UUID.randomUUID()), 4);

        assertThat(result).isPresent();
        ScheduledJobRun run = result.get();
        assertThat(run.getJobKey()).isEqualTo("SCHEDULED:" + workspaceId + ":" + filterId + ":" + fixedNow.getEpochSecond());
        assertThat(run.getStatus()).isEqualTo(JobRunStatus.PENDING);
        assertThat(run.getTriggerType()).isEqualTo(JobTriggerType.SCHEDULED);
        assertThat(run.getAttemptCount()).isZero();
        assertThat(run.getMaxAttempts()).isEqualTo(4);
    }

    @Test
    void createScheduledRun_duplicateJobKey_returnsEmptyAndCreatesNoSecondRun() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        Optional<ScheduledJobRun> result = service.createScheduledRun(
                workspaceId, filterId, fixedNow, List.of(), 4);

        assertThat(result).isEmpty();
        verify(repository, times(1)).saveAndFlush(any());
    }

    @Test
    void createManualRun_usesManualJobKeyAndSingleAttempt() {
        UUID subscriptionId = UUID.randomUUID();
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<ScheduledJobRun> result = service.createManualRun(subscriptionId, workspaceId, filterId, List.of());

        assertThat(result).isPresent();
        ScheduledJobRun run = result.get();
        assertThat(run.getJobKey()).isEqualTo("MANUAL:" + subscriptionId + ":" + fixedNow.toEpochMilli());
        assertThat(run.getTriggerType()).isEqualTo(JobTriggerType.MANUAL);
        assertThat(run.getMaxAttempts()).isEqualTo(1);
        assertThat(run.getSubscriptionId()).isEqualTo(subscriptionId);
    }

    // ── claim gate ───────────────────────────────────────────────────────────

    @Test
    void claim_repositoryReturnsOne_returnsTrue() {
        UUID runId = UUID.randomUUID();
        when(repository.claim(runId, fixedNow)).thenReturn(1);

        assertThat(service.claim(runId)).isTrue();
    }

    @Test
    void claim_alreadyClaimedRun_repositoryReturnsZero_returnsFalse() {
        UUID runId = UUID.randomUUID();
        when(repository.claim(runId, fixedNow)).thenReturn(0);

        assertThat(service.claim(runId)).isFalse();
    }

    // ── dispatch gate ────────────────────────────────────────────────────────

    @Test
    void beginDispatch_repositoryReturnsOne_returnsTrue() {
        UUID runId = UUID.randomUUID();
        when(repository.beginDispatch(runId, fixedNow)).thenReturn(1);

        assertThat(service.beginDispatch(runId)).isTrue();
    }

    @Test
    void beginDispatch_alreadyDispatching_returnsFalse() {
        UUID runId = UUID.randomUUID();
        when(repository.beginDispatch(runId, fixedNow)).thenReturn(0);

        assertThat(service.beginDispatch(runId)).isFalse();
    }

    // ── supersede ────────────────────────────────────────────────────────────

    @Test
    void supersedeOlderRuns_marksCandidatesFailedWithReason() {
        ScheduledJobRun pending = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.PENDING).build();
        ScheduledJobRun awaitingRetry = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.AWAITING_RETRY).build();
        when(repository.findSupersedeCandidates(eq(workspaceId), eq(filterId), eq(fixedNow), any()))
                .thenReturn(List.of(pending, awaitingRetry));

        List<ScheduledJobRun> superseded = service.supersedeOlderRuns(workspaceId, filterId, fixedNow);

        assertThat(superseded).hasSize(2);
        ArgumentCaptor<ScheduledJobRun> captor = ArgumentCaptor.forClass(ScheduledJobRun.class);
        verify(repository, times(2)).save(captor.capture());
        for (ScheduledJobRun saved : captor.getAllValues()) {
            assertThat(saved.getStatus()).isEqualTo(JobRunStatus.FAILED);
            assertThat(saved.getLastError()).isEqualTo("Superseded by the next scheduled run");
        }
    }

    @Test
    void supersedeOlderRuns_freshRunningRunIsNotAmongCandidates_leftAlone() {
        // The repository query itself excludes a fresh RUNNING run (claimedAt not stale) —
        // verify the service passes through whatever the repository decides is a candidate,
        // i.e. an empty list here means nothing gets touched.
        when(repository.findSupersedeCandidates(eq(workspaceId), eq(filterId), eq(fixedNow), any()))
                .thenReturn(List.of());

        List<ScheduledJobRun> superseded = service.supersedeOlderRuns(workspaceId, filterId, fixedNow);

        assertThat(superseded).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void supersedeOlderRuns_passesStaleClaimBeforeDerivedFromConfiguredWindow() {
        when(repository.findSupersedeCandidates(any(), any(), any(), any())).thenReturn(List.of());

        service.supersedeOlderRuns(workspaceId, filterId, fixedNow);

        verify(repository).findSupersedeCandidates(
                eq(workspaceId), eq(filterId), eq(fixedNow), eq(fixedNow.minusSeconds(30 * 60)));
    }

    // ── in-flight check A ────────────────────────────────────────────────────

    @Test
    void hasOtherActiveRun_delegatesWithStaleClaimWindow() {
        UUID excludeId = UUID.randomUUID();
        when(repository.existsOtherActiveRun(workspaceId, filterId, excludeId, fixedNow.minusSeconds(1800)))
                .thenReturn(true);

        assertThat(service.hasOtherActiveRun(workspaceId, filterId, excludeId)).isTrue();
    }

    // ── recovery sweep ───────────────────────────────────────────────────────

    @Test
    void sweepStaleDispatching_marksStuckRunsFailedWithoutRetry() {
        ScheduledJobRun stuck = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.DISPATCHING).build();
        when(repository.findStaleDispatching(any())).thenReturn(List.of(stuck));

        List<ScheduledJobRun> result = service.sweepStaleDispatching();

        assertThat(result).containsExactly(stuck);
        assertThat(stuck.getStatus()).isEqualTo(JobRunStatus.FAILED);
        assertThat(stuck.getNextRetryAt()).isNull();
        verify(repository).save(stuck);
    }

    @Test
    void sweepStaleDispatching_noStaleRuns_doesNothing() {
        when(repository.findStaleDispatching(any())).thenReturn(List.of());

        assertThat(service.sweepStaleDispatching()).isEmpty();
        verify(repository, never()).save(any());
    }

    // ── retry lookup ─────────────────────────────────────────────────────────

    @Test
    void findDueForRetry_delegatesWithCurrentTime() {
        service.findDueForRetry();
        verify(repository).findDueForRetry(fixedNow);
    }

    // ── terminal transitions ─────────────────────────────────────────────────

    @Test
    void markSucceeded_setsStatusAndDispatchCompletedAt() {
        ScheduledJobRun run = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.DISPATCHING).build();

        service.markSucceeded(run);

        assertThat(run.getStatus()).isEqualTo(JobRunStatus.SUCCEEDED);
        assertThat(run.getDispatchCompletedAt()).isEqualTo(fixedNow);
        verify(repository).save(run);
    }

    @Test
    void markAwaitingRetry_setsNextRetryAtAndTruncatesLongError() {
        ScheduledJobRun run = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.RUNNING).build();
        Instant nextRetry = fixedNow.plusSeconds(900);
        String longError = "x".repeat(3000);

        service.markAwaitingRetry(run, longError, nextRetry);

        assertThat(run.getStatus()).isEqualTo(JobRunStatus.AWAITING_RETRY);
        assertThat(run.getNextRetryAt()).isEqualTo(nextRetry);
        assertThat(run.getLastError()).hasSize(2000);
    }

    @Test
    void markSuppressed_setsStatus() {
        ScheduledJobRun run = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.RUNNING).build();

        service.markSuppressed(run);

        assertThat(run.getStatus()).isEqualTo(JobRunStatus.SUPPRESSED);
        verify(repository).save(run);
    }
}
