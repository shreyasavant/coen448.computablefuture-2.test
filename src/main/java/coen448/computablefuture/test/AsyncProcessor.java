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

        /**
         * Task A - Fail-Fast (Atomic Policy)
         * 
         * If any concurrent microservice invocation fails, the entire operation fails
         * and no result is produced.
         * 
         * @param services List of microservices to invoke
         * @param messages List of messages (one per service, must match size)
         * @return CompletableFuture that completes with space-separated results or
         *         fails
         */
        public CompletableFuture<String> processAsyncFailFast(
                        List<Microservice> services,
                        List<String> messages) {

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
         * Task B - Fail-Partial (Best-Effort Policy)
         * 
         * Successful microservice invocations return results, while failed invocations
         * do not abort the entire operation. Only successful results are returned.
         * 
         * @param services List of microservices to invoke
         * @param messages List of messages (one per service, must match size)
         * @return CompletableFuture that completes with list of successful results only
         */
        public CompletableFuture<List<String>> processAsyncFailPartial(
                        List<Microservice> services,
                        List<String> messages) {

                if (services.size() != messages.size()) {
                        throw new IllegalArgumentException("Services and messages lists must have same size");
                }

                List<CompletableFuture<String>> futures = java.util.stream.IntStream.range(0, services.size())
                                .mapToObj(i -> services.get(i).retrieveAsync(messages.get(i))
                                                .handle((res, ex) -> ex == null ? res : null))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .filter(res -> res != null)
                                                .collect(Collectors.toList()));
        }

        /**
         * Task C - Fail-Soft (Fallback Policy)
         * 
         * All failures are replaced with a predefined fallback value.
         * The computation never fails and always returns a complete list.
         * 
         * WARNING: This policy masks failures and may hide serious errors.
         * Use only in high-availability systems where degraded output is acceptable.
         * 
         * @param services      List of microservices to invoke
         * @param messages      List of messages (one per service, must match size)
         * @param fallbackValue Value to use when a service fails
         * @return CompletableFuture that always completes with a full list (using
         *         fallback for failures)
         */
        public CompletableFuture<List<String>> processAsyncFailSoft(
                        List<Microservice> services,
                        List<String> messages,
                        String fallbackValue) {

                if (services.size() != messages.size()) {
                        throw new IllegalArgumentException("Services and messages lists must have same size");
                }

                List<CompletableFuture<String>> futures = java.util.stream.IntStream.range(0, services.size())
                                .mapToObj(i -> services.get(i).retrieveAsync(messages.get(i))
                                                .exceptionally(ex -> fallbackValue))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .collect(Collectors.toList()));
        }
}
