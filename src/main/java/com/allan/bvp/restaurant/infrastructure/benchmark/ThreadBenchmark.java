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
 * Benchmark to compare platform threads vs virtual threads performance
 * under a blocking simulation (using Thread.sleep).
 */
@Service
@Slf4j
public class ThreadBenchmark {

    /**
     * Runs the benchmark and returns a summary of the results.
     * 
     * @param tasks the number of concurrent tasks to run
     * @param sleepTimeMs the amount of time each task blocks
     * @return a formatted string with benchmark results
     */
    public String runBenchmark(int tasks, long sleepTimeMs) {
        log.info("Starting benchmark with {} tasks, {}ms sleep each", tasks, sleepTimeMs);

        // Benchmark with Fixed Thread Pool (Platform Threads)
        // Using a reasonable number of threads for a typical server
        int platformThreads = 200;
        long platformTime = benchmark(Executors.newFixedThreadPool(platformThreads), tasks, sleepTimeMs);
        
        // Benchmark with Virtual Thread Executor
        long virtualTime = benchmark(Executors.newVirtualThreadPerTaskExecutor(), tasks, sleepTimeMs);

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
