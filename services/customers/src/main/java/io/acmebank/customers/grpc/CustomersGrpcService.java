package io.acmebank.customers.grpc;

import io.acmebank.customers.domain.Customer;
import io.acmebank.customers.persistence.CustomerRepository;
import io.acmebank.proto.customers.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/** gRPC endpoint for {@code CustomersService}; delegates persistence to {@link CustomerRepository}. */
@Component
public class CustomersGrpcService extends CustomersServiceGrpc.CustomersServiceImplBase {

    private final CustomerRepository repository;

    public CustomersGrpcService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void createCustomer(CreateCustomerRequest request, StreamObserver<CreateCustomerResponse> responseObserver) {
        try {
            Customer customer = Customer.create(
                    request.getFirstName(), request.getLastName(), request.getEmail(),
                    LocalDate.parse(request.getDateOfBirth()));
            Customer saved = repository.save(customer);
            responseObserver.onNext(CreateCustomerResponse.newBuilder().setCustomer(toProto(saved)).build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException invalid) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(invalid.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getCustomer(GetCustomerRequest request, StreamObserver<GetCustomerResponse> responseObserver) {
        Optional<Customer> found = repository.findById(request.getId());
        if (found.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("customer " + request.getId() + " not found").asRuntimeException());
            return;
        }
        responseObserver.onNext(GetCustomerResponse.newBuilder().setCustomer(toProto(found.get())).build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateCustomer(UpdateCustomerRequest request, StreamObserver<UpdateCustomerResponse> responseObserver) {
        Optional<Customer> found = repository.findById(request.getId());
        if (found.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("customer " + request.getId() + " not found").asRuntimeException());
            return;
        }
        Customer updated = new Customer(found.get().id(), request.getFirstName(), request.getLastName(),
                request.getEmail(), found.get().dateOfBirth());
        Customer saved = repository.save(updated);
        responseObserver.onNext(UpdateCustomerResponse.newBuilder().setCustomer(toProto(saved)).build());
        responseObserver.onCompleted();
    }

    private static io.acmebank.proto.customers.v1.Customer toProto(Customer c) {
        return io.acmebank.proto.customers.v1.Customer.newBuilder()
                .setId(c.id())
                .setFirstName(c.firstName())
                .setLastName(c.lastName())
                .setEmail(c.email())
                .setDateOfBirth(c.dateOfBirth().toString())
                .build();
    }
}
