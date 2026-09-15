package com.example.signupservice.cmd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example.signupservice")
@EntityScan(basePackages = "com.example.signupservice.repository")
@EnableJpaRepositories(basePackages = "com.example.signupservice.repository")
public class SignupServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignupServiceApplication.class, args);
    }
}
