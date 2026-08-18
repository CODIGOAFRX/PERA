package com.peraerp.activity.audit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditRetentionJobTest {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    void purgesExpiredEventsInBoundedBatches() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        Instant cutoff = Instant.parse("2025-08-10T12:00:00Z");
        when(repository.purgeExpired(cutoff, 1000)).thenReturn(1000, 17);
        AuditRetentionJob job = new AuditRetentionJob(repository, true, 365, 1000, 10,
                Clock.fixed(NOW, ZoneOffset.UTC));

        int deleted = job.purgeExpired();

        assertThat(deleted).isEqualTo(1017);
        verify(repository, org.mockito.Mockito.times(2)).purgeExpired(cutoff, 1000);
    }

    @Test
    void canDisableRetentionWithoutTouchingTheDatabase() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditRetentionJob job = new AuditRetentionJob(repository, false, 365, 1000, 10,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(job.purgeExpired()).isZero();
        verify(repository, never()).purgeExpired(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
