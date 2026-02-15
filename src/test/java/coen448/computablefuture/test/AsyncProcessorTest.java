package coen448.computablefuture.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.RepeatedTest;

public class AsyncProcessorTest {
    @RepeatedTest(5)
    public void testProcessAsyncSuccess() throws ExecutionException, InterruptedException {

        Microservice mockService1 = mock(Microservice.class);
        Microservice mockService2 = mock(Microservice.class);

        when(mockService1.retrieveAsync(any())).thenReturn(CompletableFuture.completedFuture("Hello"));
        when(mockService2.retrieveAsync(any())).thenReturn(CompletableFuture.completedFuture("World"));

        AsyncProcessor processor = new AsyncProcessor();
        CompletableFuture<String> resultFuture = processor.processAsync(List.of(mockService1, mockService2), null);

        String result = resultFuture.get();
        assertEquals("Hello World", result);
    }

    @ParameterizedTest
    @CsvSource({
            "hi, Hello:HI World:HI",
            "cloud, Hello:CLOUD World:CLOUD",
            "async, Hello:ASYNC World:ASYNC"
    })
    public void testProcessAsync_withDifferentMessages(
            String message,
            String expectedResult)
            throws ExecutionException, InterruptedException, TimeoutException {

        Microservice service1 = new Microservice("Hello");
        Microservice service2 = new Microservice("World");

        AsyncProcessor processor = new AsyncProcessor();

        CompletableFuture<String> resultFuture = processor.processAsync(List.of(service1, service2), message);

        String result = resultFuture.get(1, TimeUnit.SECONDS);

        assertEquals(expectedResult, result);

    }

    @RepeatedTest(20)
    void showNondeterminism_completionOrderVaries() throws Exception {

        Microservice s1 = new Microservice("A");
        Microservice s2 = new Microservice("B");
        Microservice s3 = new Microservice("C");

        AsyncProcessor processor = new AsyncProcessor();

        List<String> order = processor
                .processAsyncCompletionOrder(List.of(s1, s2, s3), "msg")
                .get(1, TimeUnit.SECONDS);

        // Not asserting a fixed order (because it is intentionally nondeterministic)
        System.out.println(order);

        // A minimal sanity check: all three must be present
        assertEquals(3, order.size());

        assertTrue(order.stream().anyMatch(x -> x.startsWith("A:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("B:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("C:")));
    }

    @Test
    public void testProcessAsyncFailPartial_MixedResults() throws Exception {
        Microservice s1 = mock(Microservice.class);
        Microservice s2 = mock(Microservice.class);
        Microservice s3 = mock(Microservice.class);

        when(s1.retrieveAsync("m1")).thenReturn(CompletableFuture.completedFuture("R1"));
        when(s2.retrieveAsync("m2")).thenAnswer(inv -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Service 2 Failure"));
            return future;
        });
        when(s3.retrieveAsync("m3")).thenReturn(CompletableFuture.completedFuture("R3"));

        AsyncProcessor processor = new AsyncProcessor();
        List<String> results = processor.processAsyncFailPartial(
                List.of(s1, s2, s3),
                List.of("m1", "m2", "m3")).get();

        // Should only contain successful results R1 and R3
        assertEquals(2, results.size());
        assertTrue(results.contains("R1"));
        assertTrue(results.contains("R3"));
        assertFalse(results.contains(null));
    }

    @Test
    public void testProcessAsyncFailPartial_AllSuccess() throws Exception {
        Microservice s1 = mock(Microservice.class);
        Microservice s2 = mock(Microservice.class);

        when(s1.retrieveAsync("m1")).thenReturn(CompletableFuture.completedFuture("R1"));
        when(s2.retrieveAsync("m2")).thenReturn(CompletableFuture.completedFuture("R2"));

        AsyncProcessor processor = new AsyncProcessor();
        List<String> results = processor.processAsyncFailPartial(
                List.of(s1, s2),
                List.of("m1", "m2")).get();

        assertEquals(2, results.size());
        assertEquals(List.of("R1", "R2"), results);
    }
}