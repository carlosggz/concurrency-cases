import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class ClassLockTest {
    private static final int MAX_TASKS = 3;
    private static final int ITERATIONS = 5;

    private final ConcurrentMap<Integer, Integer> results = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_TASKS);

    @BeforeEach
    void clearMap() {
        results.clear();
        
        IntStream
                .rangeClosed(1, MAX_TASKS)
                .forEach(x -> results.put(x, 0));
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
    void classLockTest() throws InterruptedException {
        //when
        IntStream
                .rangeClosed(1, MAX_TASKS)
                .forEach(this::generateTask);
        
        //then
        Awaitility
                .await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> results.values().stream().allMatch(v -> v == ITERATIONS));
    }

    private void generateTask(int x) {
        executor.execute(() -> {
            for (int i = 1; i <= ITERATIONS; i++) {
                System.out.printf("[Thread %d][Iteration %d] is generating id %n", x, i);
                var uniqueId = ClassLockTest.getUniqueId();
                System.out.printf("[Thread %d][Iteration %d] generated id [%s] %n", x, i, uniqueId);
                results.put(x, results.get(x) + 1);
            }
            System.out.println("Thread " + x + " finished");
        });
    }

    private static synchronized String getUniqueId() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return UUID.nameUUIDFromBytes(LocalDateTime.now().toString().getBytes(StandardCharsets.UTF_8)).toString();
    }
}
