package com.vibee.repository;

import com.vibee.entity.Order;
import com.vibee.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.ticketType", "items.ticketType.event"})
    Optional<Order> findByOrderCodeAndUserId(String orderCode, Long userId);

    @EntityGraph(attributePaths = {"items", "items.ticketType", "items.ticketType.event"})
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findDetailById(@Param("id") Long id);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    long countByUserIdAndStatus(Long userId, OrderStatus status);

    /** Đọc thẳng từ DB (không qua persistence context) để thấy trạng thái mới nhất. */
    @Query("select o.status from Order o where o.id = :id")
    Optional<OrderStatus> findStatusById(@Param("id") Long id);

    @Query("select o.id from Order o where o.status = :status and o.expiresAt < :now order by o.expiresAt asc")
    List<Long> findExpiredIds(@Param("status") OrderStatus status, @Param("now") Instant now, Pageable pageable);

    /**
     * Chuyển trạng thái nguyên tử. Hủy, hết hạn và thanh toán cùng tranh một đơn: chỉ một bên thắng
     * (trả về 1), các bên còn lại nhận 0 và biết mình thua. Nhờ vậy tồn kho không bao giờ bị hoàn hai lần.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Order o set o.status = :to where o.id = :id and o.status = :from")
    int transition(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);
}
