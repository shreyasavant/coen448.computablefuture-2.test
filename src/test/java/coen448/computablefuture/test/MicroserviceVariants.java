package coen448.computablefuture.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MicroserviceVariants {

    public static class SuccessMicroservice implements Microservice {
        private final String id;

        public SuccessMicroservice(String id) {
            this.id = id;
        }

        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            return CompletableFuture.completedFuture(id + ":" + input);
        }
    }

    public static class FailureMicroservice implements Microservice {
        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Service Failure"));
            return future;
        }
    }

    public static class DelayedMicroservice implements Microservice {
        private final String id;
        private final long delayMs;

        public DelayedMicroservice(String id, long delayMs) {
            this.id = id;
            this.delayMs = delayMs;
        }

        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return id + ":" + input;
            });
        }
    }
}
