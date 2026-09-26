package com.jose.subscriptions.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    /** El dinero se guarda en céntimos (entero): nunca float/double. */
    @Column(name = "monthly_price_cents", nullable = false)
    private int monthlyPriceCents;

    @Column(nullable = false)
    private boolean active;

    protected Plan() {
    }

    public Plan(String code, String name, int monthlyPriceCents) {
        this.code = code;
        this.name = name;
        this.monthlyPriceCents = monthlyPriceCents;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getMonthlyPriceCents() {
        return monthlyPriceCents;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
