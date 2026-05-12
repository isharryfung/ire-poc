package com.university.ire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class IreApplication {
    public static void main(String[] args) {
        SpringApplication.run(IreApplication.class, args);
    }
}
