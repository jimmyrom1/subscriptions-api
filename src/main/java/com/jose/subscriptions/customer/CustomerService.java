package com.jose.subscriptions.customer;

import java.time.Clock;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jose.subscriptions.common.BusinessException;
import com.jose.subscriptions.customer.CustomerController.CreateCustomerRequest;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    @Transactional
    public Customer create(CreateCustomerRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (customers.existsByEmailIgnoreCase(email)) {
            throw new BusinessException.Conflict("EMAIL_ALREADY_REGISTERED",
                    "Ya existe un cliente con el email " + email);
        }
        return customers.save(new Customer(email, request.fullName().trim(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Customer get(long id) {
        return customers.findById(id).orElseThrow(() -> notFound(id));
    }

    public static BusinessException notFound(long id) {
        return new BusinessException.NotFound("CUSTOMER_NOT_FOUND", "No existe el cliente " + id);
    }
}
