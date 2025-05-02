import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaserTest {
    private static final int MAX_TASKS = 3;
    private static final int MAX_PHASES = 4;

    private final Collection<String> results = Collections.synchronizedCollection(new ArrayList<>());
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
    void happyPath() {
        //given
        var phaser = new Phaser(MAX_TASKS);

        //when
        IntStream
                .rangeClosed(1, MAX_TASKS)
                .forEach(x -> generateTask(x, phaser));

        //then
        Awaitility
                .waitAtMost(MAX_TASKS * MAX_PHASES * 1100, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(MAX_TASKS * MAX_PHASES, results.size()));

        for(int i = 1; i <= MAX_TASKS; i++) {
            for (int j = 1; j <= MAX_PHASES; j++) {
                assertTrue(results.contains(i + "-" + j));
            }
        }

    }

    private void generateTask(int taskId, Phaser phaser) {
        executor.submit(() -> {
            try {
                for (int phase = 1; phase <= MAX_PHASES; phase++) {
                    System.out.println("Task " + taskId + " is starting phase " + phase);
                    Thread.sleep(1000);
                    System.out.println("Task " + taskId + " is completing phase " + phase);
                    phaser.arriveAndAwaitAdvance();
                    System.out.println("Task " + taskId + " has completed phase " + phase);
                    results.add(taskId + "-" + phase);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
