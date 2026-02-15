package coen448.computablefuture.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import coen448.computablefuture.test.MicroserviceVariants.*;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Test suite for AsyncProcessor demonstrating various failure handling
 * strategies.
 * NO Mockito is used here; instead, fake deterministic Microservice variants
 * are used.
 * 
 * NOTE: Some tests are EXPECTED to fail due to intentionally placed issues in
 * AsyncProcessor
 * to simulate a GitHub workflow.
 */
public class AsyncProcessorTest {

        private final AsyncProcessor processor = new AsyncProcessor();

        // 1️⃣ Fail-Fast Tests
        @Test
        @DisplayName("Fail-Fast: All microservices succeed")
        void failFast_allSucceed() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new SuccessMicroservice("S2"));
                String result = processor.processAsyncFailFast(services, List.of("msg1", "msg2")).get(1,
                                TimeUnit.SECONDS);
                assertEquals("S1:msg1 S2:msg2", result);
        }

        @Test
        @DisplayName("Fail-Fast: One microservice fails")
        void failFast_oneFails() {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new FailureMicroservice());
                CompletableFuture<String> future = processor.processAsyncFailFast(services, List.of("msg1", "msg2"));

                // ExecutionException wraps the actual RuntimeException from the service
                assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Fail-Fast: Multiple microservices fail")
        void failFast_multipleFail() {
                List<Microservice> services = List.of(
                                new FailureMicroservice(),
                                new FailureMicroservice());
                CompletableFuture<String> future = processor.processAsyncFailFast(services, List.of("msg1", "msg2"));
                assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        }

        // 2️⃣ Fail-Partial Tests (Expected Issue: Includes nulls)
        @Test
        @DisplayName("Fail-Partial: Partial success returns only successful results")
        void failPartial_partialSuccess() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new FailureMicroservice(),
                                new SuccessMicroservice("S3"));
                List<String> results = processor.processAsyncFailPartial(services, "msg").get(1, TimeUnit.SECONDS);

                // This test will fail if ISSUE 1 is present (it will contain null)
                assertFalse(results.contains(null), "Results should not contain null values");
                assertEquals(2, results.size(), "Should only have successful results");
        }

        @Test
        @DisplayName("Fail-Partial: All fail returns empty list")
        void failPartial_allFail() throws Exception {
                List<Microservice> services = List.of(
                                new FailureMicroservice(),
                                new FailureMicroservice());
                List<String> results = processor.processAsyncFailPartial(services, "msg").get(1, TimeUnit.SECONDS);

                // This test will fail if ISSUE 1 is present (it will contain nulls)
                assertTrue(results.isEmpty(), "Should return empty list when all fail");
        }

        // 3️⃣ Fail-Soft Tests (Expected Issue: Uses null instead of fallback)
        @Test
        @DisplayName("Fail-Soft: Fallback value appears on failure")
        void failSoft_fallbackAppears() throws Exception {
                List<Microservice> services = List.of(
                                new SuccessMicroservice("S1"),
                                new FailureMicroservice());
                String fallback = "FALLBACK";
                List<String> results = processor.processAsyncFailSoft(services, "msg", fallback).get(1,
                                TimeUnit.SECONDS);

                // This test will fail if ISSUE 2 is present (it will contain null instead of
                // FALLBACK)
                assertTrue(results.contains(fallback), "Results should contain the fallback value");
                assertEquals(2, results.size());
        }

        @Test
        @DisplayName("Fail-Soft: All fail returns all fallback values")
        void failSoft_allFail() throws Exception {
                List<Microservice> services = List.of(
                                new FailureMicroservice(),
                                new FailureMicroservice());
                String fallback = "MISSING";
                List<String> results = processor.processAsyncFailSoft(services, "msg", fallback).get(1,
                                TimeUnit.SECONDS);

                assertEquals(List.of("MISSING", "MISSING"), results);
        }

        // 4️⃣ Liveness Tests (Expected Issue: Timeout)
        @Test
        @DisplayName("Liveness: Test does not hang and respects timeout")
        void liveness_noDeadlock() throws Exception {
                List<Microservice> services = List.of(
                                new DelayedMicroservice("Slow", 500));

                // ISSUE 3: Intentional timeout issue. The service takes 500ms, but we only wait
                // 100ms.
                // In a real fix, we should either increase the timeout or ensure the service
                // completes faster.
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

                System.out.println("Completion Order Observed: " + order);

                // Confirm all are present, but DO NOT assert specific order
                assertEquals(3, order.size());
                assertTrue(order.stream().anyMatch(s -> s.startsWith("Slow:")));
                assertTrue(order.stream().anyMatch(s -> s.startsWith("Medium:")));
                assertTrue(order.stream().anyMatch(s -> s.startsWith("Fast:")));
        }
}
