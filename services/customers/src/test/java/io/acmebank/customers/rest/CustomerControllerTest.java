package io.acmebank.customers.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// grpc.port=0 binds an OS-assigned ephemeral port so each @SpringBootTest context (cached
// independently per test class because of @DynamicPropertySource) can start its own
// GrpcServerLifecycle without port 9090 colliding across concurrently cached contexts.
@SpringBootTest(properties = "grpc.port=0")
@AutoConfigureMockMvc
@Testcontainers
class CustomerControllerTest {

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
    private MockMvc mockMvc;

    @Test
    void createThenGetCustomer_roundTrips() throws Exception {
        String createBody = """
            {"firstName":"Margaret","lastName":"Hamilton","email":"margaret@example.com","dateOfBirth":"1936-08-17"}
            """;

        String location = mockMvc.perform(post("/customers").contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("margaret@example.com"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(location, "$.id");

        mockMvc.perform(get("/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("margaret@example.com"));
    }

    @Test
    void getCustomer_withUnknownId_returnsProblemDetails() throws Exception {
        mockMvc.perform(get("/customers/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateCustomer_updatesInPlace() throws Exception {
        String createBody = """
            {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","dateOfBirth":"1815-12-10"}
            """;
        String created = mockMvc.perform(post("/customers").contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        String updateBody = """
            {"firstName":"Augusta","lastName":"King","email":"augusta@example.com","dateOfBirth":"1815-12-10"}
            """;
        mockMvc.perform(put("/customers/" + id).contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("augusta@example.com"));

        mockMvc.perform(get("/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Augusta"));
    }

    @Test
    void createCustomer_withMalformedDateOfBirth_returnsProblemDetails() throws Exception {
        String createBody = """
            {"firstName":"Jane","lastName":"Doe","email":"jane@example.com","dateOfBirth":"not-a-date"}
            """;

        mockMvc.perform(post("/customers").contentType("application/json").content(createBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createCustomer_withMalformedJson_returnsProblemDetails() throws Exception {
        mockMvc.perform(post("/customers").contentType("application/json").content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
