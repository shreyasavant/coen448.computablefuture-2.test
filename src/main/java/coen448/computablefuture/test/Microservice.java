package coen448.computablefuture.test;

import java.util.concurrent.*;

class Microservice {
	
    private final String serviceId;

    public Microservice(String serviceId) {
        this.serviceId = serviceId;
    }

    // Add protected getter for subclasses
    protected String getServiceId() {
        return serviceId;
    }

    public CompletableFuture<String> retrieveAsync(String input) {
        return CompletableFuture.supplyAsync(() -> {
            // jitter: 0..30ms to perturb scheduling
            int delayMs = ThreadLocalRandom.current().nextInt(0, 31);
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return serviceId + ":" + input.toUpperCase();
        });
    }
    
}