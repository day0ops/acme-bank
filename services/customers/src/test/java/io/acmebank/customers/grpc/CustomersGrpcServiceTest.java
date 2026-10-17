package io.acmebank.customers.grpc;

import io.acmebank.customers.persistence.CustomerRepository;
import io.acmebank.proto.customers.v1.*;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

// grpc.port=0 binds an OS-assigned ephemeral port so each @SpringBootTest context (cached
// independently per test class because of @DynamicPropertySource) can start its own
// GrpcServerLifecycle without port 9090 colliding across concurrently cached contexts.
@SpringBootTest(properties = "grpc.port=0")
@Testcontainers
class CustomersGrpcServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("customers").withUsername("customers").withPassword("customers");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CustomersGrpcService service;

    private CustomersServiceGrpc.CustomersServiceBlockingStub stub;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws IOException {
        String serverName = io.grpc.inprocess.InProcessServerBuilder.generateName();
        io.grpc.Server server = InProcessServerBuilder.forName(serverName).directExecutor().addService(service).build().start();
        io.grpc.ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        stub = CustomersServiceGrpc.newBlockingStub(channel);
    }

    @Test
    void createThenGetCustomer_roundTrips() {
        CreateCustomerResponse created = stub.createCustomer(CreateCustomerRequest.newBuilder()
                .setFirstName("Linus").setLastName("Torvalds").setEmail("linus@example.com")
                .setDateOfBirth("1969-12-28").build());

        assertFalse(created.getCustomer().getId().isEmpty());

        GetCustomerResponse fetched = stub.getCustomer(GetCustomerRequest.newBuilder()
                .setId(created.getCustomer().getId()).build());

        assertEquals("linus@example.com", fetched.getCustomer().getEmail());
    }

    @Test
    void getCustomer_withUnknownId_throwsNotFound() {
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
            stub.getCustomer(GetCustomerRequest.newBuilder().setId("00000000-0000-0000-0000-000000000000").build()));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, ex.getStatus().getCode());
    }
}
