import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StructuredConcurrencyTest {

    @DisplayName("Structured Concurrency with Fork and Join with Timeout")
    @Test
    void waitForResults() throws InterruptedException, TimeoutException {
        //given
        StructuredTaskScope.Subtask<String> task1 = null;
        StructuredTaskScope.Subtask<String> task2 = null;

        //when
        try (var scope = new StructuredTaskScope<String>()) {
            task1 = fork(scope, "Task 1", 150);
            task2 = fork(scope, "Task 2", 100);

            scope
                    .joinUntil(Instant.now().plusSeconds(2));
        }

        //then
        assertEquals(StructuredTaskScope.Subtask.State.SUCCESS, task1.state());
        assertEquals(StructuredTaskScope.Subtask.State.SUCCESS, task2.state());
        assertEquals("Task 1 completed", task1.get());
        assertEquals("Task 2 completed", task2.get());
    }

    @DisplayName("Structured Concurrency with Fork and Join with Timeout and Shutdown on Failure")
    @Test
    void shutdownOnFailure() {
        //given
        StructuredTaskScope.Subtask<String> task1 = null;
        StructuredTaskScope.Subtask<String> task2 = null;

        //when
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            task1 = fork(scope, "Task 1", 200);
            task2 = fork(scope, "Task 2", 100, true);

            scope
                    .joinUntil(Instant.now().plusSeconds(1))
                    .throwIfFailed();

        } catch (InterruptedException | TimeoutException | IllegalStateException | WrongThreadException e) {
            System.out.printf("Exception running tasks: %s", e.getMessage());
        } catch (Exception e) {
            System.out.printf("Unknown exception: %s", e.getMessage());
        }

        //then
        assertEquals(StructuredTaskScope.Subtask.State.UNAVAILABLE, task1.state());
        assertEquals(StructuredTaskScope.Subtask.State.FAILED, task2.state());
    }

    @DisplayName("Structured Concurrency with Fork and Join with Timeout and Shutdown on Success")
    @Test
    void shutdownOnSuccess() throws InterruptedException, TimeoutException, ExecutionException {
        //given
        StructuredTaskScope.Subtask<String> task1 = null;
        StructuredTaskScope.Subtask<String> task2 = null;

        //when
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
            task1 = fork(scope, "Task 1", 200);
            task2 = fork(scope, "Task 2", 100);

            scope
                    .joinUntil(Instant.now().plusSeconds(1));

            assertEquals("Task 2 completed", scope.result());
        }

        //then
        assertEquals(StructuredTaskScope.Subtask.State.UNAVAILABLE, task1.state());
        assertEquals(StructuredTaskScope.Subtask.State.SUCCESS, task2.state());
    }

    private StructuredTaskScope.Subtask<String> fork(
            StructuredTaskScope scope, String taskName, int sleepTime) {
        return fork(scope, taskName, sleepTime, false);
    }

    private StructuredTaskScope.Subtask<String> fork(
            StructuredTaskScope scope, String taskName, int sleepTime, boolean throwError) {
        return scope.fork(() -> {
            System.out.println("Starting " + taskName);

            Thread.sleep(sleepTime);

            if (throwError) {
                throw new RuntimeException("Simulated error in " + taskName);
            }

            System.out.println(taskName + " completed");
            return taskName + " completed";
        });
    }
}
