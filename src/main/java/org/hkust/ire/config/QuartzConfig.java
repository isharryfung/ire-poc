package org.hkust.ire.config;

import org.hkust.ire.scheduler.job.IreProcessBatch;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz scheduler configuration for the IRE batch job.
 *
 * <p>Schedules {@link IreProcessBatch} to run every 30 minutes.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Configuration
public class QuartzConfig {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Defines the IRE process batch job detail.
     *
     * @return JobDetail for IreProcessBatch
     */
    @Bean
    public JobDetail ireProcessBatchJobDetail() {
        return JobBuilder.newJob(IreProcessBatch.class)
                .withIdentity("ireProcessBatch")
                .withDescription("IRE periodic reconciliation and maintenance job")
                .storeDurably()
                .build();
    }

    /**
     * Schedules the batch job every 30 minutes.
     *
     * @return Trigger firing every 30 minutes
     */
    @Bean
    public Trigger ireProcessBatchTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(ireProcessBatchJobDetail())
                .withIdentity("ireProcessBatchTrigger")
                .withSchedule(
                    SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(30)
                        .repeatForever())
                .build();
    }
}
