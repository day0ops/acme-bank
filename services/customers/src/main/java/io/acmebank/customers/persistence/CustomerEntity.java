package io.acmebank.customers.persistence;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customers")
class CustomerEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    protected CustomerEntity() {}

    CustomerEntity(String firstName, String lastName, String email, LocalDate dateOfBirth) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
    }

    UUID getId() { return id; }
    String getFirstName() { return firstName; }
    String getLastName() { return lastName; }
    String getEmail() { return email; }
    LocalDate getDateOfBirth() { return dateOfBirth; }
    void setFirstName(String firstName) { this.firstName = firstName; }
    void setLastName(String lastName) { this.lastName = lastName; }
    void setEmail(String email) { this.email = email; }
}
