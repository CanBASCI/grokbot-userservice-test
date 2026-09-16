package com.example.apigateway.infrastructure;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.stereotype.Component;

/**
 * Ensures W3C {@code traceparent} is present on outbound gRPC metadata (HTTP → gRPC).
 */
@Component
@GlobalClientInterceptor
public class TraceparentClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> TRACEPARENT =
            Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);

    private final ObjectProvider<Tracer> tracer;

    public TraceparentClientInterceptor(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (headers.get(TRACEPARENT) == null) {
                    String traceparent = currentTraceparent();
                    if (traceparent != null) {
                        headers.put(TRACEPARENT, traceparent);
                    }
                }
                super.start(responseListener, headers);
            }
        };
    }

    private String currentTraceparent() {
        Tracer t = tracer.getIfAvailable();
        if (t == null) {
            return null;
        }
        Span span = t.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext ctx = span.context();
        if (ctx == null || ctx.traceId() == null || ctx.spanId() == null) {
            return null;
        }
        String flags = ctx.sampled() == null || ctx.sampled() ? "01" : "00";
        return "00-" + ctx.traceId() + "-" + ctx.spanId() + "-" + flags;
    }
}
