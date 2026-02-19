package com.buildquote.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
public class BoundedParallel {

    public static <T, R> BatchResult<R> run(List<T> items, int maxConcurrent,
                                             Executor executor, Function<T, R> action) {
        if (items == null || items.isEmpty()) {
            return new BatchResult<>(Collections.emptyList(), Collections.emptyList());
        }

        Semaphore semaphore = new Semaphore(maxConcurrent);
        List<CompletableFuture<Result<R>>> futures = new ArrayList<>();

        for (T item : items) {
            CompletableFuture<Result<R>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                    try {
                        R result = action.apply(item);
                        return new Result<>(result, null);
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Result<R>(null, e);
                } catch (Exception e) {
                    return new Result<R>(null, e);
                }
            }, executor);
            futures.add(future);
        }

        List<R> successes = new ArrayList<>();
        List<Exception> failures = new ArrayList<>();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("BoundedParallel timeout or interruption: {}", e.getMessage());
        }

        for (CompletableFuture<Result<R>> f : futures) {
            try {
                Result<R> result = f.getNow(new Result<>(null, new TimeoutException("Not completed")));
                if (result.error == null && result.value != null) {
                    successes.add(result.value);
                } else if (result.error != null) {
                    failures.add(result.error);
                }
            } catch (Exception e) {
                failures.add(e);
            }
        }

        return new BatchResult<>(successes, failures);
    }

    private record Result<R>(R value, Exception error) {}

    public record BatchResult<R>(List<R> successes, List<Exception> failures) {
        public boolean hasFailures() {
            return !failures.isEmpty();
        }
    }
}
