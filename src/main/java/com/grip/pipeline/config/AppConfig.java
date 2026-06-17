package com.grip.pipeline.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared infrastructure beans. */
@Configuration
public class AppConfig {

    /** Injectable system clock — lets controllers/scheduler/tests pin "today". */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
