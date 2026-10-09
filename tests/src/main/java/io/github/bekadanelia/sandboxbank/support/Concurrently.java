package io.github.bekadanelia.sandboxbank.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

/** Fires several calls at the same moment, which is what race conditions need to show up. */
public final class Concurrently {

    private static final long TIMEOUT_SECONDS = 60;

    private Concurrently() {
    }

    /** Runs the same call {@code times} times at once and returns the results in start order. */
    public static <T> List<T> repeat(int times, Callable<T> call) {
        return run(times, index -> call);
    }

    /**
     * Runs {@code count} calls at once. Every call waits at a gate until all of them are ready,
     * so they start together instead of one after another.
     */
    public static <T> List<T> run(int count, IntFunction<Callable<T>> calls) {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch gate = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                Callable<T> call = calls.apply(index);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    gate.await();
                    return call.call();
                }));
            }
            if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The concurrent calls did not get ready in time");
            }
            gate.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } catch (ExecutionException e) {
            throw new IllegalStateException("A concurrent call failed: " + e.getCause(), e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("A concurrent call did not finish in " + TIMEOUT_SECONDS + " seconds", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent calls", e);
        }
    }
}
