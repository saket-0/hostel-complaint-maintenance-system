package edu.vit.hcms.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Thread-Safe Complaint ID Generator Concurrency Tests")
class ComplaintIdGeneratorTest {

    @Test
    @DisplayName("Format adheres strictly to HCM-YYYY-NNNNNN")
    void testIdFormat() {
        ComplaintIdGenerator generator = new ComplaintIdGenerator(2026, 0);
        String id1 = generator.nextId();
        assertEquals("HCM-2026-000001", id1);

        String id2 = generator.nextId();
        assertEquals("HCM-2026-000002", id2);
        assertTrue(id1.matches("^HCM-\\d{4}-\\d{6}$"));
    }

    @Test
    @DisplayName("Concurrency test: 50 threads generating 10,000 IDs produce zero duplicates")
    void testConcurrentIdGenerationNoDuplicates() throws InterruptedException {
        int threadCount = 50;
        int idsPerThread = 200;
        int totalExpectedIds = threadCount * idsPerThread;

        ComplaintIdGenerator generator = new ComplaintIdGenerator(2026, 0);
        Set<String> generatedIds = ConcurrentHashMap.newKeySet();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // ensure all threads start simultaneously
                    for (int j = 0; j < idsPerThread; j++) {
                        String id = generator.nextId();
                        generatedIds.add(id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Fire all threads at once
        startLatch.countDown();
        boolean finished = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All threads must complete within timeout");
        assertEquals(totalExpectedIds, generatedIds.size(),
                "All generated IDs must be unique (zero duplicates)");
        assertEquals(totalExpectedIds, generator.getCurrentSequence());
    }
}
