package com.example.apigateway.transport;

import com.example.apigateway.transport.advice.ProblemDetailExceptionHandler;
import com.example.apigateway.transport.rest.AuthController;
import com.example.auth.login.v1.LoginResponse;
import com.example.auth.login.v1.LoginServiceGrpc;
import com.example.auth.login.v1.RefreshResponse;
import com.example.auth.signup.v1.RegisterResponse;
import com.example.auth.signup.v1.SignupServiceGrpc;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({AuthController.class, ProblemDetailExceptionHandler.class})
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupServiceGrpc.SignupServiceBlockingStub signupStub;

    @MockitoBean
    private LoginServiceGrpc.LoginServiceBlockingStub loginStub;

    @Test
    void signupSuccess() throws Exception {
        String id = UUID.randomUUID().toString();
        when(signupStub.register(any())).thenReturn(
                RegisterResponse.newBuilder().setUserId(id).setEmail("user@example.com").build()
        );

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(id))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void signupEmailTaken() throws Exception {
        Metadata trailers = new Metadata();
        trailers.put(ProblemDetailExceptionHandler.ERROR_CODE_KEY, "EMAIL_TAKEN");
        when(signupStub.register(any())).thenThrow(
                Status.ALREADY_EXISTS.withDescription("Email is already registered").asRuntimeException(trailers)
        );

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
    void loginSuccess() throws Exception {
        when(loginStub.login(any())).thenReturn(
                LoginResponse.newBuilder()
                        .setAccessToken("access")
                        .setRefreshToken("refresh")
                        .setTokenType("Bearer")
                        .setExpiresIn(900)
                        .build()
        );

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginInvalidCredentials() throws Exception {
        Metadata trailers = new Metadata();
        trailers.put(ProblemDetailExceptionHandler.ERROR_CODE_KEY, "INVALID_CREDENTIALS");
        when(loginStub.login(any())).thenThrow(
                Status.UNAUTHENTICATED.withDescription("Invalid credentials").asRuntimeException(trailers)
        );

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshSuccess() throws Exception {
        when(loginStub.refresh(any())).thenReturn(
                RefreshResponse.newBuilder()
                        .setAccessToken("access2")
                        .setRefreshToken("refresh2")
                        .setTokenType("Bearer")
                        .setExpiresIn(900)
                        .build()
        );

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access2"));
    }

    @Test
    void validationError() throws Exception {
        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
