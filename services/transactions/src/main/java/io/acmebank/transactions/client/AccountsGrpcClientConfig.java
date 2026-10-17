package io.acmebank.transactions.client;

import io.acmebank.proto.accounts.v1.AccountsServiceGrpc;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the gRPC channel and stub {@link GrpcAccountsClient} depends on, using the
 * {@code accounts.grpc.host}/{@code accounts.grpc.port} properties already present in
 * {@code application.yml}. Without this bean, {@code GrpcAccountsClient} can't be constructed,
 * which breaks every {@code @SpringBootTest} in this module (the stub is an unsatisfiable
 * constructor dependency), not just production wiring.
 */
@Configuration
public class AccountsGrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel accountsChannel(@Value("${accounts.grpc.host}") String host, @Value("${accounts.grpc.port}") int port) {
        return Grpc.newChannelBuilderForAddress(host, port, InsecureChannelCredentials.create()).build();
    }

    @Bean
    public AccountsServiceGrpc.AccountsServiceBlockingStub accountsServiceBlockingStub(ManagedChannel accountsChannel) {
        return AccountsServiceGrpc.newBlockingStub(accountsChannel);
    }
}
