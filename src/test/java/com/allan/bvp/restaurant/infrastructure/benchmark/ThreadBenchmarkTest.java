package com.allan.bvp.restaurant.infrastructure.benchmark;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test to run the Threading Benchmark.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ThreadBenchmarkTest {

    @Autowired
    private ThreadBenchmark threadBenchmark;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void runAndRecordBenchmark() {
        // Run with 1000 tasks and 100ms blocking
        // This is enough to show a clear difference without taking too long
        String results = threadBenchmark.runBenchmark(1000, 100);
        assertNotNull(results);
        
        threadBenchmark.recordResults(results, "BENCHMARK_RESULTS.md");
    }
}
