package coen448.computablefuture.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.RepeatedTest;
import coen448.computablefuture.test.MicroserviceVariants.*;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Test suite for AsyncProcessor demonstrating various failure handling
 * strategies.
 * NO Mockito is used here; instead, fake deterministic Microservice variants
 * are used.
 */
public class AsyncProcessorTest {

        private final AsyncProcessor processor = new AsyncProcessor();

        @ParameterizedTest
        @ValueSource(ints = { 1, 5, 10, 25 })
        @DisplayName("PARAM: Fail-Fast scales with different service counts")
        void param_failFastScaling(int serviceCount) throws Exception {
                List<Microservice> services = IntStream.range(0, serviceCount)
                                .mapToObj(i -> (Microservice) new SuccessMicroservice("S" + i))
                                .collect(Collectors.toList());

                List<String> messages = IntStream.range(0, serviceCount)
                                .mapToObj(i -> "msg" + i)
                                .collect(Collectors.toList());

                long startTime = System.currentTimeMillis();
                String result = processor.processAsyncFailFast(services, messages)
                                .get(5, TimeUnit.SECONDS);
                long duration = System.currentTimeMillis() - startTime;

                assertEquals(serviceCount, result.split(" ").length);
                assertTrue(duration < serviceCount * 100);
        }

        @RepeatedTest(1)
        @DisplayName("STRESS: Rapid consecutive executions")
        void stress_rapidConsecutiveExecutions() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new SuccessMicroservice("S2"),
                                new SuccessMicroservice("S3"));

                List<String> messages = List.of("m1", "m2", "m3");

                for (int i = 0; i < 5; i++) {
                        String result = processor.processAsyncFailFast(services, messages)
                                        .get(500, TimeUnit.MILLISECONDS);
                        assertEquals(3, result.split(" ").length);
                }
        }

        // 1️⃣ Fail-Fast Tests
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
        @DisplayName("Fail-Fast: Size mismatch throws IllegalArgumentException")
        void failFast_sizeMismatch() {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new SuccessMicroservice("S2"));

                assertThrows(IllegalArgumentException.class,
                                () -> processor.processAsyncFailFast(services, List.of("msg1")));
        }

        // 2️⃣ Fail-Partial Tests
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
        @DisplayName("Fail-Partial: All fail returns empty list")
        void failPartial_allFail() throws Exception {
                List<Microservice> services = List.of(
                                new FailureMicroservice(),
                                new FailureMicroservice());

                List<String> results = processor.processAsyncFailPartial(
                                services,
                                List.of("msg1", "msg2"))
                                .get(1, TimeUnit.SECONDS);

                assertTrue(results.isEmpty(), "Should return empty list when all fail");
        }

        // 3️⃣ Fail-Soft Tests
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
        }

        @Test
        @DisplayName("Fail-Soft: All fail returns all fallback values")
        void failSoft_allFail() throws Exception {
                List<Microservice> services = List.of(
                                new FailureMicroservice(),
                                new FailureMicroservice());

                String fallback = "MISSING";
                List<String> results = processor.processAsyncFailSoft(
                                services,
                                List.of("msg1", "msg2"),
                                fallback)
                                .get(1, TimeUnit.SECONDS);

                assertEquals(List.of("MISSING", "MISSING"), results);
        }

        // 4️⃣ Liveness Tests
        @Test
        @DisplayName("Liveness: Test does not hang and respects timeout")
        void liveness_noDeadlock() throws Exception {
                List<Microservice> services = List.of(
                                new DelayedMicroservice("Slow", 500));

                CompletableFuture<String> future = processor.processAsync(services, "msg");

                assertDoesNotThrow(() -> {
                        future.get(2, TimeUnit.SECONDS);
                }, "Test hung or timed out prematurely");
        }

        @Test
        @DisplayName("Liveness: Get with timeout ensures test completion")
        void liveness_getWithTimeout() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("Fast"));
                String result = processor.processAsync(services, "msg").get(2, TimeUnit.SECONDS);
                assertNotNull(result);
        }

        // 5️⃣ Nondeterminism Observation
        @Test
        @DisplayName("Nondeterminism: Observe completion order")
        void nondeterminism_observeOrder() throws Exception {
                List<Microservice> services = List.of(
                                new DelayedMicroservice("Slow", 100),
                                new DelayedMicroservice("Medium", 50),
                                new SuccessMicroservice("Fast"));

                List<String> order = processor.processAsyncCompletionOrder(services, "msg").get(1, TimeUnit.SECONDS);

                assertEquals(3, order.size());
                assertTrue(order.stream().anyMatch(s -> s.startsWith("Slow:")));
                assertTrue(order.stream().anyMatch(s -> s.startsWith("Medium:")));
                assertTrue(order.stream().anyMatch(s -> s.startsWith("Fast:")));
        }
}
