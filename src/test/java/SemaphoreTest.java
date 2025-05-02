import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SemaphoreTest {

    private static final int MAX_PERMITS = 3;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void shutdownExecutor() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Semaphore allows a limited number of threads to access a resource")
    void happyPath() {
        //given
        var semaphore = new Semaphore(MAX_PERMITS);

        //when
        IntStream
                .rangeClosed(1, MAX_PERMITS+1)
                .forEach(x -> generateTask(x, semaphore, 5000));

        //then
        assertEquals(MAX_PERMITS, semaphore.availablePermits(), "All permits should be acquired");
        assertEquals(1, semaphore.getQueueLength(), "A task should be waiting in the queue");
        assertFalse(semaphore.tryAcquire(), "Semaphore should not allow more than MAX_THREADS to acquire");

        Awaitility
                .await()
                .untilAsserted(() -> assertEquals(0, semaphore.availablePermits(), "All permits should be released"));

    }

    private void generateTask(int taskId, Semaphore semaphore, int sleepTime) {
        executor.execute(() -> {
            try {
                System.out.printf("Task %d is waiting to acquire the semaphore...%n", taskId);
                semaphore.acquire();
                System.out.printf("Task %d has acquired the semaphore!%n", taskId);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                semaphore.release();
                System.out.printf("Task %d has released the semaphore!%n", taskId);
            }
        });

    }
}
