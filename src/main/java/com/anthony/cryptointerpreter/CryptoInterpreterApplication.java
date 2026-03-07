package com.anthony.cryptointerpreter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // active the engine for application
public class CryptoInterpreterApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoInterpreterApplication.class, args);
    }

}
