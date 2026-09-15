package com.example.loginservice.infrastructure;

import com.example.loginservice.domain.model.AuthenticatedUser;
import com.example.loginservice.domain.port.CredentialVerifierClient;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class HttpCredentialVerifierClient implements CredentialVerifierClient {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String internalApiKey;

    public HttpCredentialVerifierClient(RestClient restClient, String internalApiKey) {
        this.restClient = Objects.requireNonNull(restClient);
        this.internalApiKey = Objects.requireNonNull(internalApiKey);
    }

    @Override
    public Optional<AuthenticatedUser> verify(String email, String password) {
        try {
            VerifyResponse body = restClient.post()
                    .uri("/internal/v1/credentials/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(Map.of("email", email, "password", password))
                    .retrieve()
                    .body(VerifyResponse.class);
            if (body == null || body.userId() == null || body.email() == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(body.userId(), body.email()));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                return Optional.empty();
            }
            throw new IllegalStateException("credential verify failed", ex);
        }
    }

    public record VerifyResponse(UUID userId, String email) {
    }
}
