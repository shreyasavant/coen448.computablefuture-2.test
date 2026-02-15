package coen448.computablefuture.test;

import java.util.concurrent.CompletableFuture;

public interface Microservice {
    CompletableFuture<String> retrieveAsync(String input);
}
