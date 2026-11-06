package com.vibee.dto;

import com.vibee.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderCode,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant expiresAt,
        Instant createdAt,
        EventBrief event,
        List<Item> items
) {
    public record EventBrief(Long id, String title, String venue, Instant startTime, String bannerUrl) {}

    public record Item(Long ticketTypeId, String ticketTypeName, int quantity, BigDecimal unitPrice) {}
}
