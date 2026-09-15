package com.example.loginservice.infrastructure;

import com.example.loginservice.domain.model.AuthenticatedUser;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCredentialVerifierClientTest {

    private MockWebServer server;
    private HttpCredentialVerifierClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .build();
        client = new HttpCredentialVerifierClient(restClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void verifySuccess() {
        UUID id = UUID.randomUUID();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"userId":"%s","email":"user@example.com"}
                        """.formatted(id)));

        Optional<AuthenticatedUser> result = client.verify("user@example.com", "password123");
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getUserId());
        assertEquals("user@example.com", result.get().getEmail());
    }

    @Test
    void verifyUnauthorized() {
        server.enqueue(new MockResponse().setResponseCode(401)
                .addHeader("Content-Type", "application/problem+json")
                .setBody("""
                        {"code":"INVALID_CREDENTIALS"}
                        """));
        assertTrue(client.verify("user@example.com", "wrong").isEmpty());
    }

    @Test
    void verifyNon401WrapsAsIllegalState() {
        server.enqueue(new MockResponse().setResponseCode(503)
                .addHeader("Content-Type", "text/plain")
                .setBody("unavailable"));
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> client.verify("user@example.com", "password123")
        );
        assertEquals("credential verify failed", ex.getMessage());
        assertInstanceOf(Exception.class, ex.getCause());
    }
}
