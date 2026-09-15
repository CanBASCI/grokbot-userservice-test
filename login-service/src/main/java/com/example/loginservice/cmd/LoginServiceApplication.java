package com.example.loginservice.cmd;

import com.example.auth.signup.v1.SignupServiceGrpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.grpc.client.ImportGrpcClients;

@SpringBootApplication(scanBasePackages = "com.example.loginservice")
@EntityScan(basePackages = "com.example.loginservice.repository")
@EnableJpaRepositories(basePackages = "com.example.loginservice.repository")
@ImportGrpcClients(target = "signup", types = SignupServiceGrpc.SignupServiceBlockingStub.class)
public class LoginServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoginServiceApplication.class, args);
    }
}
