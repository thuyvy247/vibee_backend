package com.vibee.dto;

import com.vibee.enums.EventCategory;
import com.vibee.enums.EventStatus;

import java.time.Instant;
import java.util.List;

public record EventDetailResponse(
        Long id,
        String title,
        String description,
        String venue,
        String bannerUrl,
        Instant startTime,
        Instant endTime,
        EventStatus status,
        String rejectReason,
        Organizer organizer,
        List<TicketTypeResponse> ticketTypes,
        EventCategory category
) {
    public record Organizer(Long id, String fullName) {}
}
