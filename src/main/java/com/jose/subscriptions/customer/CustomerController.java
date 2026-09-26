package com.jose.subscriptions.customer;

import java.net.URI;
import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        var customer = service.create(request);
        return ResponseEntity.created(URI.create("/api/customers/" + customer.getId()))
                .body(CustomerResponse.from(customer));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable long id) {
        return CustomerResponse.from(service.get(id));
    }

    public record CreateCustomerRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String fullName) {
    }

    public record CustomerResponse(Long id, String email, String fullName, Instant createdAt) {
        static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getEmail(), c.getFullName(), c.getCreatedAt());
        }
    }
}
