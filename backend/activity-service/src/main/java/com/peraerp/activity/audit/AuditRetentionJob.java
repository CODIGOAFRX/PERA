package com.peraerp.activity.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AuditRetentionJob {
    private final AuditEventRepository repository;
    private final boolean enabled;
    private final int retentionDays;
    private final int batchSize;
    private final int maximumBatches;
    private final Clock clock;

    @Autowired
    public AuditRetentionJob(AuditEventRepository repository,
                             @Value("${pera.audit.retention.enabled:true}") boolean enabled,
                             @Value("${pera.audit.retention.days:365}") int retentionDays,
                             @Value("${pera.audit.retention.batch-size:1000}") int batchSize,
                             @Value("${pera.audit.retention.maximum-batches:10}") int maximumBatches) {
        this(repository, enabled, retentionDays, batchSize, maximumBatches, Clock.systemUTC());
    }

    AuditRetentionJob(AuditEventRepository repository, boolean enabled, int retentionDays, int batchSize,
                      int maximumBatches, Clock clock) {
        if (retentionDays < 30 || batchSize < 1 || batchSize > 10_000 || maximumBatches < 1) {
            throw new IllegalArgumentException("Configuración de retención de auditoría no válida.");
        }
        this.repository = repository;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.maximumBatches = maximumBatches;
        this.clock = clock;
    }

    @Scheduled(cron = "${pera.audit.retention.cron:0 17 3 * * *}")
    @Transactional
    public int purgeExpired() {
        if (!enabled) return 0;
        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        int total = 0;
        for (int batch = 0; batch < maximumBatches; batch++) {
            int deleted = repository.purgeExpired(cutoff, batchSize);
            total += deleted;
            if (deleted < batchSize) break;
        }
        return total;
    }
}
