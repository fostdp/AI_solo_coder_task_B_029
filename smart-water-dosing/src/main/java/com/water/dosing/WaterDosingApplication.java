package com.water.dosing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WaterDosingApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaterDosingApplication.class, args);
    }
}
