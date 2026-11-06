package com.vibee.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TicketTypeResponse(
        Long id,
        String name,
        BigDecimal price,
        int totalQuantity,
        int availableQuantity,
        Instant saleStart,
        Instant saleEnd,
        int maxPerOrder,
        boolean onSale      // đang trong thời gian bán, còn vé và sự kiện đã được duyệt
) {}
