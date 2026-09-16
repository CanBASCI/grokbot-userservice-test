package com.example.apigateway.transport;

import com.example.apigateway.infrastructure.LoginAuthClient;
import com.example.apigateway.infrastructure.SignupAuthClient;
import com.example.apigateway.transport.advice.ProblemDetailExceptionHandler;
import com.example.apigateway.transport.dto.SignupResponse;
import com.example.apigateway.transport.dto.TokenResponse;
import com.example.apigateway.transport.rest.AuthController;
import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private SignupAuthClient signupAuthClient;

    @MockitoBean
    private LoginAuthClient loginAuthClient;

    @Test
    void signupSuccess() throws Exception {
        String id = UUID.randomUUID().toString();
        when(signupAuthClient.register(any(), eq("key-1")))
                .thenReturn(new SignupResponse(id, "user@example.com"));

        mockMvc.perform(post("/v1/auth/signup")
                        .header("Idempotency-Key", "key-1")
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
    void signupRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("https://grokbot.local/errors/idempotency-key-required"));
    }

    @Test
    void signupEmailTaken() throws Exception {
        when(signupAuthClient.register(any(), any())).thenThrow(errorInfoStatus(
                io.grpc.Status.Code.ALREADY_EXISTS,
                "EMAIL_TAKEN",
                "auth.signup",
                "Email is already registered"
        ));

        mockMvc.perform(post("/v1/auth/signup")
                        .header("Idempotency-Key", "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
                .andExpect(jsonPath("$.type").value("https://grokbot.local/errors/email-taken"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @Test
    void loginSuccess() throws Exception {
        when(loginAuthClient.login(any())).thenReturn(
                new TokenResponse("access", "refresh", "Bearer", 900)
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
        when(loginAuthClient.login(any())).thenThrow(errorInfoStatus(
                io.grpc.Status.Code.UNAUTHENTICATED,
                "INVALID_CREDENTIALS",
                "auth.login",
                "Invalid credentials"
        ));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @Test
    void loginPasswordTooLong() throws Exception {
        String longPassword = "p".repeat(129);
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"" + longPassword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
    }

    @Test
    void refreshSuccess() throws Exception {
        when(loginAuthClient.refresh(any())).thenReturn(
                new TokenResponse("access2", "refresh2", "Bearer", 900)
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
    void refreshUnauthenticatedDefaultsToInvalidRefreshToken() throws Exception {
        Metadata trailers = new Metadata();
        trailers.put(ProblemDetailExceptionHandler.ERROR_CODE_KEY, "IGNORED_SHOULD_NOT_MATTER");
        // no ErrorInfo / blank reason via bare status without known code preference:
        // use Status without ErrorInfo and without usable trailer by clearing — actually test path fallback:
        when(loginAuthClient.refresh(any())).thenThrow(
                io.grpc.Status.UNAUTHENTICATED.withDescription("nope").asRuntimeException()
        );

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void validationError() throws Exception {
        mockMvc.perform(post("/v1/auth/signup")
                        .header("Idempotency-Key", "key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    private static StatusRuntimeException errorInfoStatus(
            io.grpc.Status.Code code,
            String reason,
            String domain,
            String message
    ) {
        ErrorInfo errorInfo = ErrorInfo.newBuilder().setReason(reason).setDomain(domain).build();
        Status status = Status.newBuilder()
                .setCode(code.value())
                .setMessage(message)
                .addDetails(Any.pack(errorInfo))
                .build();
        Metadata trailers = new Metadata();
        trailers.put(ProblemDetailExceptionHandler.ERROR_CODE_KEY, reason);
        return StatusProto.toStatusRuntimeException(status, trailers);
    }
}
