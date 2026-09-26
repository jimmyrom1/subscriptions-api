package com.jose.subscriptions.subscription;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jose.subscriptions.subscription.dto.SubscriptionDtos.PlanChangeResponse;
import com.jose.subscriptions.subscription.dto.SubscriptionDtos.PlanRequest;
import com.jose.subscriptions.subscription.dto.SubscriptionDtos.SubscriptionResponse;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping("/customers/{customerId}/subscriptions")
    public ResponseEntity<SubscriptionResponse> subscribe(@PathVariable long customerId,
                                                          @Valid @RequestBody PlanRequest request) {
        var subscription = service.subscribe(customerId, request.planCode());
        return ResponseEntity.created(URI.create("/api/subscriptions/" + subscription.getId()))
                .body(SubscriptionResponse.from(subscription));
    }

    @GetMapping("/customers/{customerId}/subscription")
    public SubscriptionResponse activeSubscription(@PathVariable long customerId) {
        return SubscriptionResponse.from(service.activeFor(customerId));
    }

    @GetMapping("/subscriptions/{id}")
    public SubscriptionResponse get(@PathVariable long id) {
        return SubscriptionResponse.from(service.get(id));
    }

    @PutMapping("/subscriptions/{id}/plan")
    public PlanChangeResponse changePlan(@PathVariable long id, @Valid @RequestBody PlanRequest request) {
        return PlanChangeResponse.from(service.changePlan(id, request.planCode()));
    }

    @PostMapping("/subscriptions/{id}/cancel")
    public SubscriptionResponse cancel(@PathVariable long id) {
        return SubscriptionResponse.from(service.cancel(id));
    }
}
