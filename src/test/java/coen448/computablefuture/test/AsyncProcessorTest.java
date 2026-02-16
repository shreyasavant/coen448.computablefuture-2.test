package coen448.computablefuture.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import coen448.computablefuture.test.MicroserviceVariants.*;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * Unit tests for AsyncProcessor failure handling policies.
 * 
 * RULES:
 * - No Mockito (we removed the exisiting mockito imports)
 * - All futures must be awaited with timeouts
 * - Tests must verify policy semantics
 */

public class AsyncProcessorTest {

        private final AsyncProcessor processor = new AsyncProcessor();

        @ParameterizedTest
        @ValueSource(ints = { 1, 5, 10, 25, 50, 100 })
        @DisplayName("PARAM: Fail-Fast scales with different service counts")
        void param_failFastScaling(int serviceCount) throws Exception {
                List<Microservice> services = IntStream.range(0, serviceCount)
                                .mapToObj(i -> (Microservice) new SuccessMicroservice("S" + i))
                                .toList();

                List<String> messages = IntStream.range(0, serviceCount)
                                .mapToObj(i -> "msg" + i)
                                .toList();

                long startTime = System.currentTimeMillis();
                String result = processor.processAsyncFailFast(services, messages)
                                .get(5, TimeUnit.SECONDS);
                long duration = System.currentTimeMillis() - startTime;

                // Verify all completed
                assertEquals(serviceCount, result.split(" ").length);

                // Should complete concurrently (not linearly)
                assertTrue(duration < serviceCount * 50,
                                serviceCount + " services took " + duration + "ms");
        }

        @RepeatedTest(20)
        @DisplayName("STRESS: Rapid consecutive executions")
        void stress_rapidConsecutiveExecutions() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new SuccessMicroservice("S2"),
                                new SuccessMicroservice("S3"));

                List<String> messages = List.of("m1", "m2", "m3");

                // Execute 10 times rapidly
                for (int i = 0; i < 10; i++) {
                        String result = processor.processAsyncFailFast(services, messages)
                                        .get(500, TimeUnit.MILLISECONDS);
                        assertEquals(3, result.split(" ").length);
                }
        }

        // FAIL-FAST TESTS

        @Test
        @DisplayName("Fail-Fast: All microservices succeed")
        void failFast_allSucceed() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new SuccessMicroservice("S2"),
                                new SuccessMicroservice("S3"));

                String result = processor.processAsyncFailFast(
                                services,
                                List.of("msg1", "msg2", "msg3"))
                                .get(1, TimeUnit.SECONDS);

                assertEquals("S1:MSG1 S2:MSG2 S3:MSG3", result);
        }

        @Test
        @DisplayName("Fail-Fast: One microservice fails")
        void failFast_oneFails() {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new FailureMicroservice(),
                                new SuccessMicroservice("S3"));

                CompletableFuture<String> future = processor.processAsyncFailFast(
                                services,
                                List.of("msg1", "msg2", "msg3"));

                ExecutionException exception = assertThrows(ExecutionException.class,
                                () -> future.get(1, TimeUnit.SECONDS));
                assertTrue(exception.getCause() instanceof RuntimeException);
        }

        @Test
        @DisplayName("Fail-Fast: Multiple microservices fail")
        void failFast_multipleFail() {
                List<Microservice> services = List.of(
                                new FailureMicroservice(),
                                new SuccessMicroservice("S2"),
                                new FailureMicroservice());

                CompletableFuture<String> future = processor.processAsyncFailFast(
                                services,
                                List.of("msg1", "msg2", "msg3"));

                assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Fail-Fast: All microservices fail")
        void failFast_allFail() {
                List<Microservice> services = List.of(
                                new FailureMicroservice(),
                                new FailureMicroservice());

                CompletableFuture<String> future = processor.processAsyncFailFast(
                                services,
                                List.of("msg1", "msg2"));

                assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Fail-Fast: Size mismatch throws IllegalArgumentException")
        void failFast_sizeMismatch() {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new SuccessMicroservice("S2"));

                assertThrows(IllegalArgumentException.class,
                                () -> processor.processAsyncFailFast(services, List.of("msg1")));
        }

        // FAIL-PARTIAL TESTS

        @Test
        @DisplayName("Fail-Partial: Partial success returns only successful results")
        void failPartial_partialSuccess() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new FailureMicroservice(),
                                new SuccessMicroservice("S3"));

                List<String> results = processor.processAsyncFailPartial(
                                services,
                                List.of("msg1", "msg2", "msg3"))
                                .get(1, TimeUnit.SECONDS);

                assertFalse(results.contains(null), "Results should not contain null");
                assertEquals(2, results.size(), "Should return exactly 2 successful results");
                assertTrue(results.contains("S1:MSG1"), "Should contain S1 result");
                assertTrue(results.contains("S3:MSG3"), "Should contain S3 result");
        }

        @Test
        @DisplayName("Fail-Partial: All succeed returns all results")
        void failPartial_allSucceed() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new SuccessMicroservice("S2"),
                                new SuccessMicroservice("S3"));

                List<String> results = processor.processAsyncFailPartial(
                                services,
                                List.of("msg1", "msg2", "msg3"))
                                .get(1, TimeUnit.SECONDS);

                assertEquals(3, results.size());
                assertTrue(results.contains("S1:MSG1"));
                assertTrue(results.contains("S2:MSG2"));
                assertTrue(results.contains("S3:MSG3"));
        }

        @Test
        @DisplayName("Fail-Partial: All fail returns empty list")
        void failPartial_allFail() throws Exception {
                List<Microservice> services = List.of(
                                new FailureMicroservice(),
                                new FailureMicroservice(),
                                new FailureMicroservice());

                List<String> results = processor.processAsyncFailPartial(
                                services,
                                List.of("msg1", "msg2", "msg3"))
                                .get(1, TimeUnit.SECONDS);

                assertTrue(results.isEmpty(), "Should return empty list when all fail");
        }

        @Test
        @DisplayName("Fail-Partial: Size mismatch throws IllegalArgumentException")
        void failPartial_sizeMismatch() {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new SuccessMicroservice("S2"));

                assertThrows(IllegalArgumentException.class,
                                () -> processor.processAsyncFailPartial(services, List.of("msg1", "msg2", "msg3")));
        }

        // FAIL-SOFT TESTS

        @Test
        @DisplayName("Fail-Soft: Fallback value appears on failure")
        void failSoft_fallbackAppears() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new FailureMicroservice(),
                                new SuccessMicroservice("S3"));

                String fallback = "FALLBACK";
                List<String> results = processor.processAsyncFailSoft(
                                services,
                                List.of("msg1", "msg2", "msg3"),
                                fallback)
                                .get(1, TimeUnit.SECONDS);

                assertEquals(3, results.size(), "Should return all 3 results");
                assertTrue(results.contains(fallback), "Should contain fallback value");
                assertTrue(results.contains("S1:MSG1"), "Should contain S1 result");
                assertTrue(results.contains("S3:MSG3"), "Should contain S3 result");
        }

        @Test
        @DisplayName("Fail-Soft: All fail returns all fallback values")
        void failSoft_allFail() throws Exception {
                List<Microservice> services = List.of(
                                new FailureMicroservice(),
                                new FailureMicroservice(),
                                new FailureMicroservice());

                String fallback = "MISSING";
                List<String> results = processor.processAsyncFailSoft(
                                services,
                                List.of("msg1", "msg2", "msg3"),
                                fallback)
                                .get(1, TimeUnit.SECONDS);

                assertEquals(List.of("MISSING", "MISSING", "MISSING"), results);
        }

        @Test
        @DisplayName("Fail-Soft: All succeed returns all actual results")
        void failSoft_allSucceed() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new SuccessMicroservice("S2"));

                String fallback = "FALLBACK";
                List<String> results = processor.processAsyncFailSoft(
                                services,
                                List.of("msg1", "msg2"),
                                fallback)
                                .get(1, TimeUnit.SECONDS);

                assertEquals(2, results.size());
                assertFalse(results.contains(fallback), "Should not contain fallback when all succeed");
                assertEquals(List.of("S1:MSG1", "S2:MSG2"), results);
        }

        @Test
        @DisplayName("Fail-Soft: Size mismatch throws IllegalArgumentException")
        void failSoft_sizeMismatch() {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"));

                assertThrows(IllegalArgumentException.class,
                                () -> processor.processAsyncFailSoft(services, List.of("msg1", "msg2"), "FALLBACK"));
        }

        // LIVENESS TESTS

        @Test
        @DisplayName("Liveness: Test does not hang and respects timeout")
        void liveness_noDeadlock() throws Exception {
                List<Microservice> services = List.of(
                                new DelayedMicroservice("Slow", 500));

                CompletableFuture<String> future = processor.processAsync(services, "msg");

                assertDoesNotThrow(() -> {
                        String result = future.get(2, TimeUnit.SECONDS);
                        assertNotNull(result);
                }, "Future should complete within timeout");
        }

        @Test
        @DisplayName("Liveness: Get with timeout ensures test completion")
        void liveness_getWithTimeout() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("Fast"));

                String result = processor.processAsync(services, "msg")
                                .get(2, TimeUnit.SECONDS);

                assertNotNull(result);
                assertEquals("Fast:MSG", result);
        }

        @Test
        @DisplayName("Liveness: Multiple delayed services complete within timeout")
        void liveness_multipleDelayed() throws Exception {
                List<Microservice> services = List.of(
                                new DelayedMicroservice("S1", 300),
                                new DelayedMicroservice("S2", 400),
                                new DelayedMicroservice("S3", 200));

                CompletableFuture<String> future = processor.processAsync(services, "msg");

                assertDoesNotThrow(() -> {
                        String result = future.get(2, TimeUnit.SECONDS);
                        assertNotNull(result);
                });
        }

        // NONDETERMINISM TESTS

        @Test
        @DisplayName("Nondeterminism: Observe completion order")
        void nondeterminism_observeOrder() throws Exception {
                List<Microservice> services = List.of(
                                new DelayedMicroservice("Slow", 150),
                                new DelayedMicroservice("Medium", 75),
                                new SuccessMicroservice("Fast"));

                List<String> order = processor.processAsyncCompletionOrder(services, "msg")
                                .get(1, TimeUnit.SECONDS);

                assertEquals(3, order.size(), "All three services should complete");
                assertTrue(order.stream().anyMatch(s -> s.startsWith("Slow:")));
                assertTrue(order.stream().anyMatch(s -> s.startsWith("Medium:")));
                assertTrue(order.stream().anyMatch(s -> s.startsWith("Fast:")));

                // Log completion order for observation (not assertion)
                System.out.println("Observed completion order: " + order);
        }

        @RepeatedTest(10)
        @DisplayName("Nondeterminism: Demonstrate order variability across runs")
        void nondeterminism_demonstrateVariability() throws Exception {
                // Services with similar delays to increase likelihood of different orderings
                List<Microservice> services = List.of(
                                new DelayedMicroservice("A", 50),
                                new DelayedMicroservice("B", 50),
                                new DelayedMicroservice("C", 50));

                List<String> order = processor.processAsyncCompletionOrder(services, "msg")
                                .get(1, TimeUnit.SECONDS);

                assertEquals(3, order.size());
                System.out.println("Run completion order: " + order);

                // Note: We observe variability but don't assert on specific order
                // because nondeterminism is a fundamental property of concurrent execution
        }
}