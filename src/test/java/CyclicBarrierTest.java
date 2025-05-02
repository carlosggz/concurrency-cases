import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CyclicBarrierTest {

    private static final int MAX_PARTIES = 3;

    private final Collection<String> results = Collections.synchronizedCollection(new ArrayList<>());
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_PARTIES);

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
    void happyPath()  {
        //given
        var cyclicBarrier = new CyclicBarrier(MAX_PARTIES, this::onBarrierTripped);

        //when
        IntStream
                .rangeClosed(1, MAX_PARTIES)
                .forEach(x -> generateOkTask(x, cyclicBarrier, 1_000 * x));

        //then
        Awaitility
                .waitAtMost(MAX_PARTIES+1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(MAX_PARTIES, results.size()));

        assertEquals(0, cyclicBarrier.getNumberWaiting());
        IntStream
                .rangeClosed(1, MAX_PARTIES)
                .forEach(x -> assertTrue(results.contains("Task " + x), "Task " + x + " should be completed"));
    }

    @Test
    void unhappyPathTimeout()  {
        //given
        var cyclicBarrier = new CyclicBarrier(MAX_PARTIES, this::onBarrierTripped);

        //when
        IntStream
                .range(1, MAX_PARTIES)
                .forEach(x -> generateOkTask(x, cyclicBarrier, 1_000 * x));

        generateOkTask(MAX_PARTIES, cyclicBarrier, 3_000*MAX_PARTIES);

        //then
        Awaitility
                .waitAtMost(MAX_PARTIES+1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, cyclicBarrier.getNumberWaiting()));

        assertEquals(0, results.size());
    }

    @Test
    void unhappyPathTaskDoesNotReachBarrier()  {
        //given
        var cyclicBarrier = new CyclicBarrier(MAX_PARTIES, this::onBarrierTripped);

        //when
        IntStream
                .range(1, MAX_PARTIES)
                .forEach(x -> generateOkTask(x, cyclicBarrier, 1_000 * x));

        generateKoTask(MAX_PARTIES, cyclicBarrier, 1_000*MAX_PARTIES + 500);

        //then
        Awaitility
                .waitAtMost(MAX_PARTIES+1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, cyclicBarrier.getNumberWaiting()));

        assertEquals(0, results.size());
    }

    private void generateOkTask(int id, CyclicBarrier cyclicBarrier, int timeToSleep) {
        executor.execute(() -> {
            var name = "Task " + id;
            try {
                System.out.println(name + " is starting...");
                Thread.sleep(timeToSleep);
                System.out.printf("%s is done sleeping for %d ms%n", name, timeToSleep);
                System.out.printf("%s is waiting for barrier...%n", name);
                cyclicBarrier.await(MAX_PARTIES+1, TimeUnit.SECONDS);
                System.out.printf("%s is continuing after the barrier%n", name);
                results.add(name);
                System.out.println(name + " is done.");
            } catch (InterruptedException e) {
                System.out.println(name + " was interrupted");
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                System.out.printf("%s was broken%n", name);
            } catch (TimeoutException e) {
                System.out.printf("%s timed out%n", name);
            }
        });
    }

    private void generateKoTask(int id, CyclicBarrier cyclicBarrier, int timeToSleep) {
        executor.execute(() -> {
            var name = "Task " + id;
            System.out.println(name + " is starting...");

            try {
                Thread.sleep(timeToSleep);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            System.out.printf("%s is done sleeping for %d ms%n", name, timeToSleep);
            System.out.printf("%s is failing with threads %d waiting for the barrier...%n",
                    name, cyclicBarrier.getNumberWaiting());
            //do not use cyclicBarrier.reset() unless you know it it the last thread
        });
    }

    private void onBarrierTripped() {
        System.out.println("Barrier tripped!");
    }
}
