package org.hkust.ire.scheduler;

import org.hkust.ire.db.persistence.service.batch.BatchJobService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;

/**
 * Tests for the batch job service.
 *
 * @author ire-team
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
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
