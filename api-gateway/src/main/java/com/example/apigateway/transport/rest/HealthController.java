package com.example.apigateway.transport.rest;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final GrpcChannelFactory channelFactory;

    public HealthController(GrpcChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
    }

    @GetMapping(path = "/ready", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> ready() {
        return Map.of("status", "UP");
    }

    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        boolean signupUp = isReachable("signup");
        boolean loginUp = isReachable("login");
        boolean up = signupUp && loginUp;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", up ? "UP" : "DOWN");
        body.put("signup", signupUp ? "UP" : "DOWN");
        body.put("login", loginUp ? "UP" : "DOWN");
        return ResponseEntity.status(up ? 200 : 503).body(body);
    }

    private boolean isReachable(String channelName) {
        try {
            ManagedChannel channel = channelFactory.createChannel(channelName);
            ConnectivityState state = channel.getState(true);
            return state != ConnectivityState.SHUTDOWN && state != ConnectivityState.TRANSIENT_FAILURE;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
