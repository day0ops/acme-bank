package io.acmebank.transactions.rest;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// grpc.port=0 binds an OS-assigned ephemeral port so each @SpringBootTest context (cached
// independently per test class because of @DynamicPropertySource) can start its own
// GrpcServerLifecycle without port 9090 colliding across concurrently cached contexts.
@SpringBootTest(properties = "grpc.port=0")
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

    private static final KeyPair TEST_KEY_PAIR;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            TEST_KEY_PAIR = generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Overrides the production JWKS-based JwtDecoder (which targets the unreachable
    // jwk-set-uri from application.yml) so tokens signed with TEST_KEY_PAIR validate
    // through the real SecurityConfig.jwtAuthenticationConverter() role-mapping logic.
    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) TEST_KEY_PAIR.getPublic()).build();
        }
    }

    private static String signTestToken(List<String> roles) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user-id")
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .claim("realm_access", Map.of("roles", roles))
                .build();
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                claims);
        signedJWT.sign(new RSASSASigner(TEST_KEY_PAIR.getPrivate()));
        return signedJWT.serialize();
    }

    private static String customerToken() throws Exception {
        return signTestToken(List.of("customer"));
    }

    @Test
    void getTransaction_withUnknownId_returnsProblemDetails() throws Exception {
        mockMvc.perform(get("/transactions/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + customerToken()))
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
