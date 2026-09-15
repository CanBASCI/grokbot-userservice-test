package com.example.apigateway.cmd;

import com.example.auth.login.v1.LoginServiceGrpc;
import com.example.auth.signup.v1.SignupServiceGrpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;

@SpringBootApplication(scanBasePackages = "com.example.apigateway")
@ImportGrpcClients(target = "signup", types = SignupServiceGrpc.SignupServiceBlockingStub.class)
@ImportGrpcClients(target = "login", types = LoginServiceGrpc.LoginServiceBlockingStub.class)
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
