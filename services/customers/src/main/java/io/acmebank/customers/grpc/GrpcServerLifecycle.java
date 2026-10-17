package io.acmebank.customers.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Starts and stops the gRPC server alongside the Spring application context. */
@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private final CustomersGrpcService service;
    private final OpenTelemetry openTelemetry;
    private final int port;
    private Server server;
    private volatile boolean running = false;

    public GrpcServerLifecycle(CustomersGrpcService service, OpenTelemetry openTelemetry,
                                @Value("${grpc.port:9090}") int port) {
        this.service = service;
        this.openTelemetry = openTelemetry;
        this.port = port;
    }

    @Override
    public void start() {
        GrpcTelemetry telemetry = GrpcTelemetry.create(openTelemetry);
        try {
            server = ServerBuilder.forPort(port)
                    .addService(service)
                    .intercept(telemetry.newServerInterceptor())
                    .build()
                    .start();
            running = true;
        } catch (IOException e) {
            throw new IllegalStateException("failed to start gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
