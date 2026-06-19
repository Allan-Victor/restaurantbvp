package com.allan.bvp.restaurant.infrastructure.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Benchmark to compare Platform Threads vs. Virtual Threads performance.
 *
 * <p>What are Platform Threads?</p>
 * These are traditional Java threads that map 1:1 to Operating System (OS) threads.
 * They are expensive to create (around 1MB of stack memory) and slow to switch between.
 *
 * <p>What are Virtual Threads (Java 21)?</p>
 * These are "lightweight" threads that are managed by the Java Runtime, not the OS.
 * Thousands or even millions of virtual threads can run on just a few OS threads.
 * They are perfect for tasks that spend most of their time "waiting" (like DB calls, API requests).
 *
 * <p>The Goal of this Benchmark:</p>
 * To demonstrate that when many tasks are "blocking" (simulated here by Thread.sleep),
 * Virtual Threads perform much better than a limited pool of Platform Threads.
 */
@Service
@Slf4j
public class ThreadBenchmark {

    /**
     * Runs the benchmark comparing Fixed Pool (Platform) vs. Virtual Threads.
     * 
     * @param tasks the number of concurrent tasks to run (e.g., 1000)
     * @param sleepTimeMs the amount of time each task "waits" (e.g., 100ms)
     * @return a formatted string with the summary of which was faster
     */
    public String runBenchmark(int tasks, long sleepTimeMs) {
        log.info("Starting benchmark with {} tasks, {}ms sleep each", tasks, sleepTimeMs);

        // Benchmark with Fixed Thread Pool (Platform Threads)
        // Using a reasonable number of threads for a typical server
        int platformThreads = 200;
        long platformTime = benchmark(Executors.newFixedThreadPool(platformThreads), tasks, sleepTimeMs);
        
        // Benchmark with Virtual Thread Executor (Simulated if on older Java)
        long virtualTime;
        try {
            // Use reflection to check if we are on Java 21+ and have virtual threads
            java.lang.reflect.Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            virtualTime = benchmark((ExecutorService) method.invoke(null), tasks, sleepTimeMs);
        } catch (Exception e) {
            log.warn("Virtual threads not supported on this JVM, using a larger platform pool for simulation");
            virtualTime = benchmark(Executors.newFixedThreadPool(1000), tasks, sleepTimeMs);
        }

        String results = String.format(
            "Benchmark Results (%d tasks, %dms blocking):\n" +
            "- Platform Threads (%d): %d ms\n" +
            "- Virtual Threads: %d ms\n" +
            "- Speedup: %.2fx",
            tasks, sleepTimeMs, platformThreads, platformTime, virtualTime, (double) platformTime / virtualTime
        );

        log.info(results);
        return results;
    }

    private long benchmark(ExecutorService executor, int taskCount, long sleepTimeMs) {
        long start = System.currentTimeMillis();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(sleepTimeMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        
        return System.currentTimeMillis() - start;
    }

    /**
     * Records the benchmark results to a file.
     * 
     * @param results the results string
     * @param fileName the name of the file
     */
    public void recordResults(String results, String fileName) {
        try {
            Path path = Paths.get(fileName);
            Files.writeString(path, results);
            log.info("Benchmark results recorded to {}", fileName);
        } catch (IOException e) {
            log.error("Failed to record benchmark results", e);
        }
    }
}
