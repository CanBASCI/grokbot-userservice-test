package com.example.signupservice.cmd;

import com.example.signupservice.domain.port.PasswordHasher;
import com.example.signupservice.domain.port.UserRepository;
import com.example.signupservice.domain.usecase.SignupService;
import com.example.signupservice.domain.usecase.VerifyCredentialsService;
import com.example.signupservice.infrastructure.BCryptPasswordHasher;
import com.example.signupservice.repository.SpringDataUserJpaRepository;
import com.example.signupservice.repository.UserRepositoryAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    UserRepository userRepository(SpringDataUserJpaRepository jpaRepository) {
        return new UserRepositoryAdapter(jpaRepository);
    }

    @Bean
    SignupService signupService(UserRepository userRepository, PasswordHasher passwordHasher) {
        return new SignupService(userRepository, passwordHasher);
    }

    @Bean
    VerifyCredentialsService verifyCredentialsService(
            UserRepository userRepository,
            PasswordHasher passwordHasher
    ) {
        return new VerifyCredentialsService(userRepository, passwordHasher);
    }
}
