package com.vibee.service;

import com.vibee.dto.EventDetailResponse;
import com.vibee.dto.EventSummaryResponse;
import com.vibee.dto.TicketTypeResponse;
import com.vibee.entity.Event;
import com.vibee.entity.TicketType;
import com.vibee.enums.EventStatus;
import com.vibee.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Chuyển entity sang response. Gọi trong transaction để lazy-load hoạt động. */
@Component
@RequiredArgsConstructor
public class EventAssembler {

    private final TicketTypeRepository ticketTypeRepository;

    /** 2 query cho cả trang: một lấy sự kiện (kèm organizer), một lấy toàn bộ hạng vé của trang đó. */
    public Page<EventSummaryResponse> toSummaryPage(Page<Event> page) {
        List<Long> ids = page.getContent().stream().map(Event::getId).toList();
        Map<Long, List<TicketType>> typesByEvent = ids.isEmpty() ? Map.of()
                : ticketTypeRepository.findByEventIdIn(ids).stream()
                        .collect(Collectors.groupingBy(t -> t.getEvent().getId()));
        return page.map(e -> toSummary(e, typesByEvent.getOrDefault(e.getId(), List.of())));
    }

    public EventDetailResponse toDetail(Event e) {
        return toDetail(e, ticketTypeRepository.findByEventIdOrderByPriceAscIdAsc(e.getId()));
    }

    public EventDetailResponse toDetail(Event e, List<TicketType> types) {
        boolean published = e.getStatus() == EventStatus.PUBLISHED;
        return new EventDetailResponse(
                e.getId(), e.getTitle(), e.getDescription(), e.getVenue(), e.getBannerUrl(),
                e.getStartTime(), e.getEndTime(), e.getStatus(), e.getRejectReason(),
                new EventDetailResponse.Organizer(e.getOrganizer().getId(), e.getOrganizer().getFullName()),
                types.stream().map(t -> toTicketType(t, published)).toList(),
                e.getCategory());
    }

    public TicketTypeResponse toTicketType(TicketType t, boolean eventPublished) {
        Instant now = Instant.now();
        boolean inWindow = !now.isBefore(t.getSaleStart()) && !now.isAfter(t.getSaleEnd());
        return new TicketTypeResponse(t.getId(), t.getName(), t.getPrice(), t.getTotalQuantity(),
                t.getAvailableQuantity(), t.getSaleStart(), t.getSaleEnd(), t.getMaxPerOrder(),
                eventPublished && inWindow && t.getAvailableQuantity() > 0);
    }

    private EventSummaryResponse toSummary(Event e, List<TicketType> types) {
        BigDecimal minPrice = types.stream().map(TicketType::getPrice).min(Comparator.naturalOrder()).orElse(null);
        boolean soldOut = !types.isEmpty() && types.stream().allMatch(t -> t.getAvailableQuantity() == 0);
        return new EventSummaryResponse(e.getId(), e.getTitle(), e.getVenue(), e.getBannerUrl(),
                e.getStartTime(), e.getEndTime(), e.getStatus(), e.getOrganizer().getFullName(), minPrice, soldOut, e.getCategory());
    }
}
