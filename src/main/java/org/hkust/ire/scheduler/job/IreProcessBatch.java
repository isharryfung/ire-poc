package org.hkust.ire.scheduler.job;

import org.hkust.ire.db.persistence.service.batch.BatchJobService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Quartz batch job for periodic IRE maintenance tasks.
 *
 * <p>Executes every 30 minutes (configured in {@link org.hkust.ire.config.QuartzConfig}).
 * Runs: identity reconciliation, audit log archival, metrics snapshot.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class IreProcessBatch extends QuartzJobBean {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private BatchJobService batchJobService;

    /**
     * Main job execution method called by the Quartz scheduler.
     *
     * @param context the Quartz job execution context
     * @throws JobExecutionException if the job fails
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("IreProcessBatch started at {}", context.getFireTime());
        try {
            batchJobService.runBatchTasks();
            log.info("IreProcessBatch completed successfully");
        } catch (Exception e) {
            log.error("IreProcessBatch failed: {}", e.getMessage());
            throw new JobExecutionException(e);
        }
    }
}
