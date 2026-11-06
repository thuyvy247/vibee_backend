package com.vibee.repository;

import com.vibee.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select oi from OrderItem oi join fetch oi.ticketType tt join fetch tt.event where oi.order.id in :orderIds")
    List<OrderItem> findByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
