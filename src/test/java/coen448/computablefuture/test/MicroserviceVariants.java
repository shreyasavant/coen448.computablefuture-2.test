package coen448.computablefuture.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Test variants of Microservice for testing different failure scenarios.
 */
public class MicroserviceVariants {

    /**
     * Always succeeds with a predefined response (no jitter for deterministic testing).
     */
    public static class SuccessMicroservice extends Microservice {
        
        public SuccessMicroservice(String serviceId) {
            super(serviceId);
        }
        
        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            // Override to provide deterministic success without jitter
            return CompletableFuture.completedFuture(getServiceId() + ":" + input.toUpperCase());
        }
    }

    /**
     * Always fails with a RuntimeException.
     */
    public static class FailureMicroservice extends Microservice {
        
        public FailureMicroservice() {
            super("FAIL");
        }
        
        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Service Failure"));
            return future;
        }
    }

    /**
     * Succeeds after a specified delay.
     */
    public static class DelayedMicroservice extends Microservice {
        private final long delayMs;

        public DelayedMicroservice(String serviceId, long delayMs) {
            super(serviceId);
            this.delayMs = delayMs;
        }

        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return getServiceId() + ":" + input.toUpperCase();
            });
        }
    }
}