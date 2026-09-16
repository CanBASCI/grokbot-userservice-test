package com.example.signupservice.transport.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
public class IdempotencyKeyInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> IDEMPOTENCY_METADATA_KEY =
            Metadata.Key.of("idempotency-key", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> IDEMPOTENCY_KEY =
            Context.key("idempotency-key");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String key = headers.get(IDEMPOTENCY_METADATA_KEY);
        Context context = Context.current().withValue(IDEMPOTENCY_KEY, key);
        return Contexts.interceptCall(context, call, headers, next);
    }
}
