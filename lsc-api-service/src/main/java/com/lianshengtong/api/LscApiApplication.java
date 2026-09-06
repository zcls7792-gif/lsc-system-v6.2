package com.lianshengtong.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LscApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LscApiApplication.class, args);
        System.out.println("[LSC API Service] 运行于 http://localhost:8000");
    }
}
