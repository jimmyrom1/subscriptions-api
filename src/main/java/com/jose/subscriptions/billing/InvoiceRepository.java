package com.jose.subscriptions.billing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("""
            select i from Invoice i
            join fetch i.subscription s
            join fetch s.plan
            where s.customer.id = :customerId
            order by i.periodStart desc, i.id desc
            """)
    List<Invoice> findByCustomerId(@Param("customerId") Long customerId);

    long countBySubscriptionId(Long subscriptionId);
}
