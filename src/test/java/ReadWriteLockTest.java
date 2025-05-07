import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReadWriteLockTest {
    @Test
    void testReadWriteLock() throws InterruptedException {
        //given
        var readWriteLock = new ReentrantReadWriteLock();
        var readLock = readWriteLock.readLock();
        var writeLock = readWriteLock.writeLock();
        var atomicInteger = new AtomicInteger(0);

        var threads = List.of(
                new CustomThread("Reader1", readLock, atomicInteger),
                new CustomThread("Reader2", readLock, atomicInteger),
                new CustomThread("Writer1", writeLock, atomicInteger),
                new CustomThread("Writer2", writeLock, atomicInteger),
                new CustomThread("Reader3", readLock, atomicInteger),
                new CustomThread("Reader4", readLock, atomicInteger)
        );

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

        private final String name;
        private final Lock lock;
        private final AtomicInteger atomicInteger;

        public CustomThread(String name, Lock lock, AtomicInteger atomicInteger) {
            this.name = name;
            this.lock = lock;
            this.atomicInteger = atomicInteger;
        }

        @Override
        public void run() {
            System.out.printf("Thread %s is trying to acquire the lock %n", name);
            lock.lock();
            System.out.printf("Thread %s has acquired the lock%n", name);
            try {
                sleep(3000); // Simulate some work with the lock
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            lock.unlock();
            System.out.printf("Thread %s has released the lock%n", name);
            atomicInteger.incrementAndGet();
            System.out.printf("Thread %s has been completed %n", name);
        }
    }
}
