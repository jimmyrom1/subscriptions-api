package com.jose.subscriptions.billing;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceController {

    private final BillingService billing;

    public InvoiceController(BillingService billing) {
        this.billing = billing;
    }

    @GetMapping("/api/customers/{customerId}/invoices")
    public List<InvoiceResponse> invoices(@PathVariable long customerId) {
        return billing.invoicesFor(customerId).stream().map(InvoiceResponse::from).toList();
    }
}
