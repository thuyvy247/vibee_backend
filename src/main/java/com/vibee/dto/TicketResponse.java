package com.vibee.dto;

import com.vibee.enums.TicketStatus;

import java.time.Instant;

public record TicketResponse(
        Long id,
        String code,          // nội dung mã QR
        TicketStatus status,
        Instant checkedInAt,
        String ticketTypeName,
        String orderCode,
        EventBrief event
) {
    public record EventBrief(Long id, String title, String venue, Instant startTime, Instant endTime, String bannerUrl) {}
}
