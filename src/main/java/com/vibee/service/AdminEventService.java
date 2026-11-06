package com.vibee.service;

import com.vibee.dto.EventDetailResponse;
import com.vibee.dto.EventSummaryResponse;
import com.vibee.dto.PageResponse;
import com.vibee.entity.Event;
import com.vibee.enums.EventStatus;
import com.vibee.exception.ApiException;
import com.vibee.repository.EventRepository;
import com.vibee.repository.EventSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final EventRepository eventRepository;
    private final EventAssembler assembler;

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> list(EventStatus status, int page, int size) {
        // Hàng đợi duyệt: cũ nhất lên trước
        Sort sort = status == EventStatus.PENDING
                ? Sort.by("createdAt").ascending()
                : Sort.by("createdAt").descending();
        var pageable = PageResponse.pageable(page, size, sort);
        Specification<Event> spec = Specification.where(EventSpecs.hasStatus(status));
        return PageResponse.from(assembler.toSummaryPage(eventRepository.findAll(spec, pageable)));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse get(Long id) {
        return assembler.toDetail(find(id));
    }

    /** PENDING -> PUBLISHED. */
    @Transactional
    public EventDetailResponse approve(Long id) {
        Event event = find(id);
        requirePending(event);
        if (!event.getStartTime().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_ALREADY_STARTED", "The event start time is in the past");
        }
        event.setStatus(EventStatus.PUBLISHED);
        event.setRejectReason(null);
        return assembler.toDetail(event);
    }

    /** PENDING -> DRAFT kèm lý do, để organizer sửa rồi gửi lại. */
    @Transactional
    public EventDetailResponse reject(Long id, String reason) {
        Event event = find(id);
        requirePending(event);
        event.setStatus(EventStatus.DRAFT);
        event.setRejectReason(reason.trim());
        return assembler.toDetail(event);
    }

    private Event find(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
    }

    private void requirePending(Event event) {
        if (event.getStatus() != EventStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_EVENT_STATE", "Only PENDING events can be reviewed");
        }
    }
}
