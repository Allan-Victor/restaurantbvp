package com.allan.bvp.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Restaurant Management System application.
 */
@SpringBootApplication
@EnableScheduling
public class RestaurantApplication {

    /**
     * Bootstraps the Spring Boot application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(RestaurantApplication.class, args);
    }

}
