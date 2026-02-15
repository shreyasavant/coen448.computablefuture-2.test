package coen448.computablefuture.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AsyncProcessor {

        public CompletableFuture<String> processAsync(List<Microservice> microservices, String message) {
                List<CompletableFuture<String>> futures = microservices.stream()
                                .map(client -> client.retrieveAsync(message))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .collect(Collectors.joining(" ")));
        }

        public CompletableFuture<List<String>> processAsyncCompletionOrder(
                        List<Microservice> microservices, String message) {

                List<String> completionOrder = Collections.synchronizedList(new ArrayList<>());

                List<CompletableFuture<Void>> futures = microservices.stream()
                                .map(ms -> ms.retrieveAsync(message)
                                                .thenAccept(completionOrder::add))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> completionOrder);
        }

        public CompletableFuture<String> processAsyncFailFast(List<Microservice> services, List<String> messages) {
                if (services.size() != messages.size()) {
                        throw new IllegalArgumentException("Services and messages lists must have same size");
                }

                List<CompletableFuture<String>> futures = java.util.stream.IntStream.range(0, services.size())
                                .mapToObj(i -> services.get(i).retrieveAsync(messages.get(i)))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .collect(Collectors.joining(" ")));
        }

        /**
         * Logic for Fail-Partial: return only successful results.
         */
        public CompletableFuture<List<String>> processAsyncFailPartial(List<Microservice> services, String message) {
                List<CompletableFuture<String>> futures = services.stream()
                                .map(ms -> ms.retrieveAsync(message)
                                                .handle((res, ex) -> ex == null ? res : null))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .filter(res -> res != null)
                                                .collect(Collectors.toList()));
        }

        /**
         * Logic for Fail-Soft: use fallback value on failure.
         */
        public CompletableFuture<List<String>> processAsyncFailSoft(List<Microservice> services, String message,
                        String fallback) {
                List<CompletableFuture<String>> futures = services.stream()
                                .map(ms -> ms.retrieveAsync(message)
                                                .exceptionally(ex -> fallback))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .collect(Collectors.toList()));
        }

}
