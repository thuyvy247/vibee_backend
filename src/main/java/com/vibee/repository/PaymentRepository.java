package com.vibee.repository;

import com.vibee.entity.Payment;
import com.vibee.enums.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "order")
    Optional<Payment> findByTxnRef(String txnRef);

    long countByOrderId(Long orderId);

    /** PENDING -> SUCCESS nguyên tử: IPN/return gọi nhiều lần thì chỉ lần đầu trả về 1 (idempotent). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Payment p
               set p.status = com.vibee.enums.PaymentStatus.SUCCESS,
                   p.providerTxnNo = :providerTxnNo, p.rawResponse = :raw, p.paidAt = :now
             where p.id = :id and p.status = com.vibee.enums.PaymentStatus.PENDING
            """)
    int markSuccess(@Param("id") Long id, @Param("providerTxnNo") String providerTxnNo,
                    @Param("raw") String raw, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Payment p
               set p.status = com.vibee.enums.PaymentStatus.FAILED, p.rawResponse = :raw
             where p.id = :id and p.status = com.vibee.enums.PaymentStatus.PENDING
            """)
    int markFailed(@Param("id") Long id, @Param("raw") String raw);

    @Modifying(flushAutomatically = true)
    @Query("update Payment p set p.status = :status where p.id = :id")
    int setStatus(@Param("id") Long id, @Param("status") PaymentStatus status);
}
