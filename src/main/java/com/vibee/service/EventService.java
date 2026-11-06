package com.vibee.service;

import com.vibee.dto.EventDetailResponse;
import com.vibee.dto.EventSummaryResponse;
import com.vibee.dto.PageResponse;
import com.vibee.dto.TicketTypeResponse;
import com.vibee.entity.Event;
import com.vibee.enums.EventCategory;
import com.vibee.enums.EventSort;
import com.vibee.enums.EventStatus;
import com.vibee.exception.ApiException;
import com.vibee.repository.EventRepository;
import com.vibee.repository.EventSpecs;
import com.vibee.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** API công khai: chỉ thấy sự kiện đã PUBLISHED. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventAssembler assembler;

    public PageResponse<EventSummaryResponse> search(String keyword, String venue, EventCategory category,
                                                     Instant from, Instant to, EventSort sort,
                                                     int page, int size) {
        Specification<Event> spec = Specification
                .where(EventSpecs.hasStatus(EventStatus.PUBLISHED))
                .and(EventSpecs.endsAfter(Instant.now()))       // ẩn sự kiện đã kết thúc
                .and(EventSpecs.titleContains(keyword))
                .and(EventSpecs.venueContains(venue))
                .and(EventSpecs.hasCategory(category))
                .and(EventSpecs.startsFrom(from))
                .and(EventSpecs.startsBefore(to));

        Sort order = sort == EventSort.NEWEST ? Sort.by("createdAt").descending() : Sort.by("startTime").ascending();
        var pageable = PageResponse.pageable(page, size, order);
        return PageResponse.from(assembler.toSummaryPage(eventRepository.findAll(spec, pageable)));
    }

    public EventDetailResponse getPublished(Long id) {
        return assembler.toDetail(findPublished(id));
    }

    public List<TicketTypeResponse> getTicketTypes(Long eventId) {
        findPublished(eventId);
        return ticketTypeRepository.findByEventIdOrderByPriceAscIdAsc(eventId).stream()
                .map(t -> assembler.toTicketType(t, true))
                .toList();
    }

    private Event findPublished(Long id) {
        return eventRepository.findById(id)
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
    }
}
