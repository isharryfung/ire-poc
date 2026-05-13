package org.hkust.ire.scheduler;

import org.hkust.ire.db.persistence.service.batch.BatchJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for the batch job service.
 *
 * @author ire-team
 * @since 1.0.0
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
public class IreProcessBatchTest {

    @Autowired
    private BatchJobService batchJobService;

    @Test
    public void testBatchJobServiceLoads() {
        assertNotNull(batchJobService);
    }

    @Test
    public void testRunBatchTasksDoesNotThrow() {
        batchJobService.runBatchTasks();
    }
}
