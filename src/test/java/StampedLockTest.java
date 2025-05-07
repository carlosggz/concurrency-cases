import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.locks.StampedLock;

import static org.junit.jupiter.api.Assertions.*;

public class StampedLockTest {

    @Test
    void happyPathWrite() {
        //given
        var lock = new StampedLock();

        //when
        var stamp = lock.writeLock();

        //then
        assertTrue(lock.isWriteLocked());
        assertFalse(lock.isReadLocked());
        assertTrue(lock.validate(stamp));
        assertEquals(0L, lock.tryWriteLock());
        assertEquals(0L, lock.tryReadLock());

        lock.unlockWrite(stamp);
    }

    @Test
    void happyPathRead() {
        //given
        var lock = new StampedLock();

        //when
        var stamp1 = lock.readLock();
        var stamp2 = lock.readLock();
        var stamp3 = lock.tryReadLock();

        //then
        assertFalse(lock.isWriteLocked());
        assertTrue(lock.isReadLocked());
        assertTrue(lock.validate(stamp1));
        assertTrue(lock.validate(stamp2));
        assertTrue(lock.validate(stamp3));
        assertEquals(0L, lock.tryWriteLock());

        lock.unlockRead(stamp1);
        assertTrue(lock.isReadLocked());
        assertEquals(0L, lock.tryWriteLock());
        lock.unlockRead(stamp2);
        assertTrue(lock.isReadLocked());
        assertEquals(0L, lock.tryWriteLock());
        lock.unlockRead(stamp3);
        assertFalse(lock.isReadLocked());
    }

    @ParameterizedTest
    @ValueSource(ints = {2,4})
    void retries(int maxRetries) {
        //given
        var lock = new StampedLock();

        var threadWrite = new Thread(() -> writeOperation(lock));
        threadWrite.setName("writeThread");
        var threadRead = new Thread(() -> readOperation(lock, maxRetries));
        threadRead.setName("readThread");

        //when
        threadWrite.start();
        threadRead.start();

        //then
        Awaitility
                .await()
                .untilAsserted(() -> assertTrue(!threadWrite.isAlive() && !threadRead.isAlive()));
        assertFalse(lock.isWriteLocked());
        assertFalse(lock.isReadLocked());
    }

    private void readOperation(StampedLock lock, int maxRetries) {
        long stamp;
        var currentRetry = 0;
        sleep(100);

        do {
            currentRetry++;
            System.out.printf("Thread %s is trying to acquire the lock %n", Thread.currentThread().getName());
            stamp = lock.tryOptimisticRead(); // non-blocking read

            if (stamp == 0L) {
                System.out.printf("Thread %s failed to acquire the lock, retrying... %n", Thread.currentThread().getName());
                sleep(400);
            }

        } while (currentRetry < maxRetries && stamp == 0L);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            System.out.println("Thread " + Thread.currentThread().getName() + " has acquired the lock");
            sleep(100);
            lock.unlockRead(stamp);
            System.out.printf("Thread %s has released the lock%n", Thread.currentThread().getName());
        } else {
            System.out.printf("Thread %s has acquired the lock with optimistic read%n", Thread.currentThread().getName());
        }
    }

    private void writeOperation(StampedLock lock) {
        System.out.println("Thread " + Thread.currentThread().getName() + " is trying to acquire the lock");
        var stamp = lock.writeLock();
        System.out.printf("Thread %s has acquired the lock%n", Thread.currentThread().getName());
        try {
            System.out.println("Thread " + Thread.currentThread().getName() + " is doing some work");
            sleep(1000);
        } finally {
            lock.unlockWrite(stamp);
            System.out.printf("Thread %s has released the lock%n", Thread.currentThread().getName());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
