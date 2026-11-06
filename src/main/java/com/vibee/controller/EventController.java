package com.vibee.controller;

import com.vibee.dto.EventDetailResponse;
import com.vibee.dto.EventSummaryResponse;
import com.vibee.dto.PageResponse;
import com.vibee.dto.TicketTypeResponse;
import com.vibee.enums.EventCategory;
import com.vibee.enums.EventSort;
import com.vibee.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /** from/to là ISO-8601, ví dụ 2026-12-01T00:00:00Z (lọc theo giờ bắt đầu). */
    @GetMapping
    public PageResponse<EventSummaryResponse> search(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String venue,
                                                     @RequestParam(required = false) EventCategory category,
                                                     @RequestParam(required = false) Instant from,
                                                     @RequestParam(required = false) Instant to,
                                                     @RequestParam(defaultValue = "SOONEST") EventSort sort,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "12") int size) {
        return eventService.search(keyword, venue, category, from, to, sort, page, size);
    }

    @GetMapping("/{id}")
    public EventDetailResponse get(@PathVariable Long id) {
        return eventService.getPublished(id);
    }

    @GetMapping("/{id}/ticket-types")
    public List<TicketTypeResponse> ticketTypes(@PathVariable Long id) {
        return eventService.getTicketTypes(id);
    }
}
