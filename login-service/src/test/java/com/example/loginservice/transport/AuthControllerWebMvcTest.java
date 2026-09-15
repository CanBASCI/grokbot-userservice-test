package com.example.loginservice.transport;

import com.example.loginservice.domain.error.DomainException;
import com.example.loginservice.domain.error.ErrorCode;
import com.example.loginservice.domain.model.TokenPair;
import com.example.loginservice.domain.usecase.LoginService;
import com.example.loginservice.domain.usecase.RefreshService;
import com.example.loginservice.transport.advice.ProblemDetailExceptionHandler;
import com.example.loginservice.transport.dto.TokenResponse;
import com.example.loginservice.transport.mapper.LoginTransportMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({AuthController.class, ProblemDetailExceptionHandler.class, AuthControllerWebMvcTest.TestBeans.class})
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private RefreshService refreshService;

    @Test
    void loginSuccess() throws Exception {
        when(loginService.login("user@example.com", "password123"))
                .thenReturn(new TokenPair("access", "refresh", "Bearer", 900));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginInvalidCredentials() throws Exception {
        when(loginService.login(anyString(), anyString()))
                .thenThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS, 401, "Unauthorized", "Invalid credentials"));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshSuccess() throws Exception {
        when(refreshService.refresh("old-refresh"))
                .thenReturn(new TokenPair("access2", "refresh2", "Bearer", 900));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access2"))
                .andExpect(jsonPath("$.refreshToken").value("refresh2"));
    }

    @Test
    void refreshInvalid() throws Exception {
        when(refreshService.refresh(anyString()))
                .thenThrow(new DomainException(
                        ErrorCode.INVALID_REFRESH_TOKEN,
                        401,
                        "Unauthorized",
                        "Invalid refresh token"
                ));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"bad"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestBeans {
        @Bean
        LoginTransportMapper loginTransportMapper() {
            return pair -> new TokenResponse(
                    pair.getAccessToken(),
                    pair.getRefreshToken(),
                    pair.getTokenType(),
                    pair.getExpiresIn()
            );
        }
    }
}
