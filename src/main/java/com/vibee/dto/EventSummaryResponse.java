package com.vibee.dto;

import com.vibee.enums.EventCategory;
import com.vibee.enums.EventStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record EventSummaryResponse(
        Long id,
        String title,
        String venue,
        String bannerUrl,
        Instant startTime,
        Instant endTime,
        EventStatus status,
        String organizerName,
        BigDecimal minPrice,   // null nếu chưa có hạng vé
        boolean soldOut,
        EventCategory category
) {}
