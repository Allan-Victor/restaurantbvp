package com.allan.bvp.restaurant;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for the {@link RestaurantApplication} context.
 * These tests verify that the Spring application context loads correctly.
 */
@SpringBootTest
@Disabled("Disabled due to environmental SQL dialect issues with H2 and TIMESTAMPTZ")
class RestaurantApplicationTests {

    @Test
    void contextLoads() {
    }

}
