package com.jose.subscriptions.plan;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanRepository plans;

    public PlanController(PlanRepository plans) {
        this.plans = plans;
    }

    @GetMapping
    public List<PlanResponse> activePlans() {
        return plans.findByActiveTrueOrderByMonthlyPriceCentsAsc().stream().map(PlanResponse::from).toList();
    }

    public record PlanResponse(String code, String name, int monthlyPriceCents, String currency) {
        static PlanResponse from(Plan plan) {
            return new PlanResponse(plan.getCode(), plan.getName(), plan.getMonthlyPriceCents(), "EUR");
        }
    }
}
