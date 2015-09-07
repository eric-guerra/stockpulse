package com.ericguerra.stockpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Repositories are nested interfaces in domain.Repositories; Spring Data skips nested ones unless told otherwise.
@EnableJpaRepositories(considerNestedRepositories = true)
@EnableScheduling
public class StockPulseApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockPulseApplication.class, args);
    }
}
