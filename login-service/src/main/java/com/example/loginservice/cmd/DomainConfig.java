package com.example.loginservice.cmd;

import com.example.auth.signup.v1.SignupServiceGrpc;
import com.example.loginservice.domain.port.CredentialVerifierClient;
import com.example.loginservice.domain.port.RefreshTokenRepository;
import com.example.loginservice.domain.port.TokenIssuer;
import com.example.loginservice.domain.usecase.LoginService;
import com.example.loginservice.domain.usecase.RefreshService;
import com.example.loginservice.infrastructure.GrpcCredentialVerifierClient;
import com.example.loginservice.infrastructure.JwtAccessTokenIssuer;
import com.example.loginservice.repository.RefreshTokenRepositoryAdapter;
import com.example.loginservice.repository.SpringDataRefreshTokenJpaRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class DomainConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TokenIssuer tokenIssuer(AppProperties properties, Clock clock) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is required and must be at least 32 characters"
            );
        }
        return new JwtAccessTokenIssuer(
                secret,
                properties.getJwt().getAccessTtlSeconds(),
                clock
        );
    }

    @Bean
    CredentialVerifierClient credentialVerifierClient(SignupServiceGrpc.SignupServiceBlockingStub signupStub) {
        return new GrpcCredentialVerifierClient(signupStub);
    }

    @Bean
    RefreshTokenRepository refreshTokenRepository(
            SpringDataRefreshTokenJpaRepository jpaRepository,
            Clock clock
    ) {
        return new RefreshTokenRepositoryAdapter(jpaRepository, clock);
    }

    @Bean
    LoginService loginService(
            CredentialVerifierClient credentialVerifierClient,
            RefreshTokenRepository refreshTokenRepository,
            TokenIssuer tokenIssuer,
            AppProperties properties,
            Clock clock
    ) {
        return new LoginService(
                credentialVerifierClient,
                refreshTokenRepository,
                tokenIssuer,
                properties.getRefresh().getTtlSeconds(),
                clock
        );
    }

    @Bean
    RefreshService refreshService(
            RefreshTokenRepository refreshTokenRepository,
            TokenIssuer tokenIssuer,
            LoginService loginService,
            Clock clock
    ) {
        return new RefreshService(
                refreshTokenRepository,
                tokenIssuer,
                loginService,
                clock
        );
    }
}
