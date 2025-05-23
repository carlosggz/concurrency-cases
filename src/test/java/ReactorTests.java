import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.RetryBackoffSpec;

public class ReactorTests {

    @ParameterizedTest
    @MethodSource("values")
    void iterableTest(Flux<Integer> flux) {
        StepVerifier
            .create(flux)
            .expectNext(1)
            .expectNext(2)
            .expectNext(3)
            .expectNext(4)
            .verifyComplete();
    }

    @ParameterizedTest
    @MethodSource("values")
    void filterTest(Flux<Integer> flux) {
        StepVerifier
            .create(flux.filter(x -> x % 2 == 0)
            )
            .expectNext(2)
            .expectNext(4)
            .verifyComplete();
    }

    @ParameterizedTest
    @MethodSource("values")
    void mapTest(Flux<Integer> flux) {
        var flow = flux
            .map(x -> x * 10);

        StepVerifier
            .create(flow)
            .expectNext(10)
            .expectNext(20)
            .expectNext(30)
            .expectNext(40)
            .verifyComplete();
    }

    @Test
    void fromCallableTest() {
        var flow = Mono
            .fromCallable(() -> {
                //To use non-reactive calls
                return 123;
            });

        StepVerifier
            .create(flow)
            .expectNext(123)
            .verifyComplete();
    }

    @Test
    void errorResultTest() {

        var mono = Mono.error(new RuntimeException("test"));

        StepVerifier
            .create(mono)
            .expectError(RuntimeException.class)
            .verify();
    }

    @Test
    void onErrorMap() {

        var flow = Flux
            .just(0, 1)
            .map(x -> 12/x)
            .onErrorMap(x -> new IllegalAccessException("my message"));

        StepVerifier
            .create(flow)
            .expectError(IllegalAccessException.class)
            .verify();
    }

    @Test
    void onErrorResume() {
        var flow = Flux
            .just(-1, 0, 1)
            .map(x -> 12/x)
            .onErrorResume(x -> Flux.just(10, 11));

        StepVerifier
            .create(flow)
            .expectNext(-12)
            .expectNext(10)
            .expectNext(11)
            .verifyComplete();
    }

    @Test
    void onErrorContinue() {

        var flow = Flux
            .just(-1, 0, 1)
            .map(x -> 12/x)
            .onErrorContinue((error, value) -> System.out.println("Error: " + error.getMessage()));

        StepVerifier
            .create(flow)
            .expectNext(-12)
            .expectNext(12)
            .verifyComplete();
    }

    @Test
    void retriesTest() {

        var count = new AtomicInteger(0);
        var flow = Mono
            .just(0)
            .doOnNext(x -> count.incrementAndGet())
            .map(x -> 12/x)
            .retry(2);

        StepVerifier
            .create(flow)
            .expectError(ArithmeticException.class)
            .verify();

        assertEquals(3, count.get());
    }

    @Test
    void retriesWithBackOffTest() {

        var count = new AtomicInteger(0);
        var flow = Mono
            .just(0)
            .doOnNext(x -> count.incrementAndGet())
            .map(x -> 12/x)
            .retryWhen(RetryBackoffSpec
                .backoff(2, Duration.ofMillis(100))
                .doBeforeRetry(x -> System.out.println("Retrying because " + x.failure().getMessage()+".........."))
                .onRetryExhaustedThrow((x,y) -> new IllegalArgumentException("Max of retries reached"))
            );

        StepVerifier
            .create(flow)
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    @Test
    void mergeTest()  {

        var flux1 = Flux
            .just(1,3,5,7)
            .delayElements(Duration.ofMillis(200));
        var flux2 = Flux
            .just(2,4,6,8)
            .delayElements(Duration.ofMillis(100));
        var flow = Flux.merge(flux1, flux2); //merge when arrives any

        StepVerifier
            .create(flow)
            .expectNext(2)
            .expectNext(1)
            .expectNext(4)
            .expectNext(6)
            .expectNext(3)
            .expectNext(8)
            .expectNext(5)
            .expectNext(7)
            .verifyComplete();
    }

    @Test
    void concatTest() {

        var flux1 = Flux
            .just(1,3,5,7)
            .delayElements(Duration.ofMillis(200));
        var flux2 = Flux
            .just(2,4,6,8)
            .delayElements(Duration.ofMillis(100));
        var flow = Flux.concat(flux1, flux2); //waits for flux1 completion and then start flux2

        StepVerifier
            .create(flow)
            .expectNext(1)
            .expectNext(3)
            .expectNext(5)
            .expectNext(7)
            .expectNext(2)
            .expectNext(4)
            .expectNext(6)
            .expectNext(8)
            .verifyComplete();
    }

    @Test
    void zipWithTest() {

        var flux1 = Flux.just(1);
        var flux2 = Flux.just(2);
        var flow = flux1.zipWith(flux2); //parallel;

        StepVerifier
            .create(flow)
            .expectNextMatches(x -> x.getT1() == 1 && x.getT2() == 2)
            .verifyComplete();
    }

    @Test
    void zipWhenTest() {

        var flux1 = Flux.just(1);
        var flux2 = Flux.just(2);
        var flow = flux1
            .next()
            .zipWhen(x -> flux2.next()); //Waits for it

        StepVerifier
            .create(flow)
            .expectNextMatches(x -> x.getT1() == 1 && x.getT2() == 2)
            .verifyComplete();
    }

    @Test
    void takeTest() {
        var flow = Flux
            .just(1,2,3,4,5,6)
            .take(2);

        StepVerifier
            .create(flow)
            .expectNext(1)
            .expectNext(2)
            .verifyComplete();
    }

    @Test
    void takeLastTest() {
        var flow = Flux
            .just(1,2,3,4,5,6)
            .takeLast(2);

        StepVerifier
            .create(flow)
            .expectNext(5)
            .expectNext(6)
            .verifyComplete();
    }

    @Test
    void takeUntilTest() {
        var flow = Flux
            .just(1,2,3,4,5,6)
            .takeUntil(x -> x > 3);

        StepVerifier
            .create(flow)
            .expectNext(1)
            .expectNext(2)
            .expectNext(3)
            .expectNext(4)
            .verifyComplete();
    }

    @Test
    void takeWhileTest() {
        var flow = Flux
            .just(1,2,3,4,5,6)
            .takeWhile(x -> x <= 3);

        StepVerifier
            .create(flow)
            .expectNext(1)
            .expectNext(2)
            .expectNext(3)
            .verifyComplete();
    }

    private static Stream<Arguments> values() {
        return Stream.of(
            Arguments.of(Flux.just(1,2,3,4)),
            Arguments.of(Flux.range(1,4)),
            Arguments.of(Flux.fromIterable(List.of(1,2,3,4))),
            Arguments.of(Flux.fromStream(Stream.of(1,2,3,4))),
            Arguments.of(Flux.fromArray(new Integer[] {1,2,3,4}))
        );
    }
}
