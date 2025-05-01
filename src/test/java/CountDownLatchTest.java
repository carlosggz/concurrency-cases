import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class CountDownLatchTest {
    private static final int MAX_TASKS = 3;

    private final ConcurrentMap<Integer, Boolean> results = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_TASKS);

    @BeforeEach
    void clearMap() {
        results.clear();
    }

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
    @DisplayName("All tasks are completed successfully")
    void happyPath() throws InterruptedException {
        //given
        var countDownLatch = new CountDownLatch(MAX_TASKS);
        IntStream
                .rangeClosed(1, MAX_TASKS)
                .forEach(x -> generateOkTask(x,countDownLatch, 1_000*x));

        //when
        var result = countDownLatch.await(MAX_TASKS+1, java.util.concurrent.TimeUnit.SECONDS);

        //then
        assertTrue(result, "All tasks should complete within the timeout");
        assertEquals(MAX_TASKS, results.size());
        IntStream
                .rangeClosed(1, MAX_TASKS)
                .forEach(x -> assertTrue(results.get(x), "Task " + x + " should be completed"));
    }

    @Test
    @DisplayName("One task times out")
    void unhappyTimeout() throws InterruptedException {
        //given
        var countDownLatch = new CountDownLatch(MAX_TASKS);
        IntStream
                .range(1, MAX_TASKS)
                .forEach(x -> generateOkTask(x,countDownLatch, 1_000*x));

        generateOkTask(MAX_TASKS,countDownLatch, 20_000);

        //when
        var result = countDownLatch.await(MAX_TASKS+1, java.util.concurrent.TimeUnit.SECONDS);

        //then
        assertFalse(result, "One task should timeout");

        assertEquals(MAX_TASKS-1, results.size());
        IntStream
                .range(1, MAX_TASKS)
                .forEach(x -> assertTrue(results.get(x), "Task " + x + " should be completed"));

        assertFalse(results.containsKey(MAX_TASKS));
    }

    @Test
    @DisplayName("One task fails and resets the latch")
    void unhappyAndReset() throws InterruptedException {
        //given
        var countDownLatch = new CountDownLatch(MAX_TASKS);
        IntStream
                .range(1, MAX_TASKS)
                .forEach(x -> generateOkTask(x, countDownLatch, 1_000*x));

        generateKoTask(MAX_TASKS, countDownLatch, 100);

        //when
        var result = countDownLatch.await(MAX_TASKS+1, java.util.concurrent.TimeUnit.SECONDS);

        //then
        assertTrue(result, "One task failed and reset the count down latch");

        assertEquals(1, results.size());
        assertTrue(results.containsKey(MAX_TASKS));
        assertFalse(results.get(MAX_TASKS));
    }

    private void generateOkTask(int id, CountDownLatch countDownLatch, int timeToSleep) {
        executor.execute(() -> {
            var name = "Task " + id;
            try {
                System.out.println(name + " is starting...");
                Thread.sleep(timeToSleep);
                System.out.printf("%s is done sleeping for %d ms%n", name, timeToSleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                System.out.printf("%s is counting down the latch...%n", name);
                results.put(id, true);
                countDownLatch.countDown();
                System.out.println(name + " has counted down the latch.");
            }
        });
    }

    private void generateKoTask(int id, CountDownLatch countDownLatch, int timeToSleep) {
        executor.execute(() -> {
            var name = "Task " + id;
            try {
                System.out.println(name + " is starting...");
                Thread.sleep(timeToSleep);
                System.out.printf("%s is done sleeping for %d ms%n", name, timeToSleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                System.out.printf("%s is resetting down the latch...%n", name);
                results.put(id, false);
                while (countDownLatch.getCount() > 0) {
                    countDownLatch.countDown();
                }
                countDownLatch.countDown();
                System.out.println(name + " has reset down the latch.");
            }
        });
    }
}
