package com.example.loginservice.cmd;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootEnvValidator implements ApplicationRunner {

    private final AppProperties properties;

    public BootEnvValidator(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is required and must be at least 32 characters"
            );
        }
        String baseUrl = properties.getSignup().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("SIGNUP_BASE_URL is required");
        }
    }
}
