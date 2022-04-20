package com.legiz.terasoftproject;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition
@SpringBootApplication
public class TerasoftProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(TerasoftProjectApplication.class, args);
    }

}
