package com.jose.subscriptions.subscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByCustomerIdAndStatus(Long customerId, SubscriptionStatus status);

    @EntityGraph(attributePaths = {"plan", "customer"})
    Optional<Subscription> findWithPlanById(Long id);

    @EntityGraph(attributePaths = {"plan", "customer"})
    Optional<Subscription> findFirstByCustomerIdAndStatus(Long customerId, SubscriptionStatus status);

    /** Solo los ids: cada renovación carga su suscripción en su propia transacción. */
    @Query("""
            select s.id from Subscription s
            where s.status = :status and s.currentPeriodEnd <= :now
            order by s.currentPeriodEnd
            """)
    List<Long> findDueIds(@Param("status") SubscriptionStatus status, @Param("now") Instant now);
}
