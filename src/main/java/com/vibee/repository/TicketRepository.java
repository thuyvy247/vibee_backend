package com.vibee.repository;

import com.vibee.entity.Ticket;
import com.vibee.entity.User;
import com.vibee.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @EntityGraph(attributePaths = {"event", "orderItem", "orderItem.ticketType", "orderItem.order", "orderItem.order.user"})
    Optional<Ticket> findByCode(String code);

    @Query(value = """
            select t from Ticket t
              join fetch t.event e
              join fetch t.orderItem oi
              join fetch oi.ticketType tt
              join fetch oi.order o
             where o.user.id = :userId
            """,
            countQuery = "select count(t) from Ticket t where t.orderItem.order.user.id = :userId")
    Page<Ticket> findByOwner(@Param("userId") Long userId, Pageable pageable);

    @Query("select t.status from Ticket t where t.code = :code")
    Optional<TicketStatus> findStatusByCode(@Param("code") String code);

    /** VALID -> USED nguyên tử. Hai nhân viên quét cùng một vé cùng lúc thì chỉ một người nhận 1. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Ticket t
               set t.status = com.vibee.enums.TicketStatus.USED, t.checkedInAt = :now, t.checkedInBy = :staff
             where t.code = :code and t.status = com.vibee.enums.TicketStatus.VALID
            """)
    int markUsed(@Param("code") String code, @Param("now") Instant now, @Param("staff") User staff);
}
