import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReentrantLockTest {
    @Test
    void testReentrantLock() throws InterruptedException {
        //given
        var reentrantLock = new ReentrantLock();
        var atomicInteger = new AtomicInteger(0);

        var threads = IntStream
                .rangeClosed(1, 3)
                .mapToObj(i -> new CustomThread(reentrantLock, atomicInteger))
                .toList();

        //when
        for (var thread : threads) {
            thread.start();
        }

        for (var thread : threads) {
            thread.join();
        }


        //then
        Awaitility
                .await()
                .untilAsserted(() -> assertEquals(threads.size(), atomicInteger.get()));
    }

    private static class CustomThread extends Thread {

        private final Lock lock;
        private final AtomicInteger atomicInteger;

        public CustomThread(Lock lock, AtomicInteger atomicInteger) {
            this.lock = lock;
            this.atomicInteger = atomicInteger;
        }

        @Override
        public void run() {
            System.out.println("Thread " + Thread.currentThread().getName() + " is trying to acquire the lock");
            lock.lock();
            System.out.printf("Thread %s has acquired the lock%n", Thread.currentThread().getName());
            try {
                sleep(3000); // Simulate some work with the lock
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            lock.unlock();
            System.out.printf("Thread %s has released the lock%n", Thread.currentThread().getName());
            atomicInteger.incrementAndGet();
            System.out.printf("Thread %s has been completed %n", Thread.currentThread().getName());
        }
    }
}
