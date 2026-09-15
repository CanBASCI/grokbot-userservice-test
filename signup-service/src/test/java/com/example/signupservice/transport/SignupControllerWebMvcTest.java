package com.example.signupservice.transport;

import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.usecase.SignupService;
import com.example.signupservice.domain.usecase.VerifyCredentialsService;
import com.example.signupservice.transport.advice.ProblemDetailExceptionHandler;
import com.example.signupservice.transport.dto.SignupResponse;
import com.example.signupservice.transport.dto.VerifyCredentialsResponse;
import com.example.signupservice.transport.mapper.SignupTransportMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SignupController.class, InternalCredentialsController.class})
@Import({SignupController.class, InternalCredentialsController.class, ProblemDetailExceptionHandler.class, SignupControllerWebMvcTest.TestBeans.class})
class SignupControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupService signupService;

    @MockitoBean
    private VerifyCredentialsService verifyCredentialsService;

    @Test
    void signupSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        when(signupService.signup("user@example.com", "password123"))
                .thenReturn(new User(id, "user@example.com", "hash", Instant.now()));

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(id.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void signupEmailTaken() throws Exception {
        when(signupService.signup(anyString(), anyString()))
                .thenThrow(new DomainException(ErrorCode.EMAIL_TAKEN, 409, "Conflict", "Email is already registered"));

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void signupValidationCode() throws Exception {
        when(signupService.signup(anyString(), anyString()))
                .thenThrow(new DomainException(ErrorCode.PASSWORD_TOO_SHORT, 400, "Bad Request", "too short"));

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
    }

    @Test
    void signupUnknownProperty() throws Exception {
        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123","extra":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PROPERTY"));
    }

    @Test
    void verifySuccess() throws Exception {
        UUID id = UUID.randomUUID();
        when(verifyCredentialsService.verify("user@example.com", "password123"))
                .thenReturn(new User(id, "user@example.com", "hash", Instant.now()));

        mockMvc.perform(post("/internal/v1/credentials/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void verifyInvalidCredentials() throws Exception {
        when(verifyCredentialsService.verify(anyString(), anyString()))
                .thenThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS, 401, "Unauthorized", "Invalid credentials"));

        mockMvc.perform(post("/internal/v1/credentials/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestBeans {
        @Bean
        SignupTransportMapper signupTransportMapper() {
            return new SignupTransportMapper() {
                @Override
                public SignupResponse toSignupResponse(User user) {
                    return new SignupResponse(user.getId(), user.getEmail());
                }

                @Override
                public VerifyCredentialsResponse toVerifyResponse(User user) {
                    return new VerifyCredentialsResponse(user.getId(), user.getEmail());
                }
            };
        }
    }
}
