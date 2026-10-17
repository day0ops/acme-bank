package io.acmebank.transactions.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transactions").withUsername("transactions").withPassword("transactions");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    // SecurityMockMvcRequestPostProcessors.jwt() bypasses SecurityConfig's JwtDecoder/
    // JwtAuthenticationConverter beans entirely, so authorities must be supplied explicitly
    // here to mirror what that converter grants for this realm role.
    private static RequestPostProcessor customerJwt() {
        return jwt()
                .jwt(builder -> builder.claim("realm_access", Map.of("roles", List.of("customer"))))
                .authorities(new SimpleGrantedAuthority("ROLE_customer"));
    }

    @Test
    void getTransaction_withUnknownId_returnsProblemDetails() throws Exception {
        mockMvc.perform(get("/transactions/00000000-0000-0000-0000-000000000000").with(customerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void transfer_withoutToken_returns401() throws Exception {
        String transferBody = """
            {"fromAccountId":"00000000-0000-0000-0000-000000000000","toAccountId":"00000000-0000-0000-0000-000000000001","currencyCode":"USD","amountMinorUnits":1000}
            """;

        mockMvc.perform(post("/transactions").contentType("application/json").content(transferBody))
                .andExpect(status().isUnauthorized());
    }
}
