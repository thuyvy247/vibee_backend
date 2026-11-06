package com.vibee.service;

import com.vibee.dto.*;
import com.vibee.entity.Event;
import com.vibee.entity.TicketType;
import com.vibee.entity.User;
import com.vibee.enums.EventCategory;
import com.vibee.enums.EventStatus;
import com.vibee.exception.ApiException;
import com.vibee.repository.EventRepository;
import com.vibee.repository.EventSpecs;
import com.vibee.repository.TicketTypeRepository;
import com.vibee.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Quy tắc cốt lõi: sự kiện và hạng vé chỉ sửa được khi ở trạng thái DRAFT.
 * Nhờ vậy lúc chưa duyệt thì chưa có đơn nào, tồn kho luôn bằng tổng số vé, và không phải xử lý
 * xung đột giữa "organizer đổi số lượng" với "người mua đang trừ tồn kho".
 */
@Service
@RequiredArgsConstructor
public class OrganizerEventService {

    private static final int DEFAULT_MAX_PER_ORDER = 5;

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserRepository userRepository;
    private final EventAssembler assembler;

    // ---------------- Event ----------------

    @Transactional
    public EventDetailResponse create(Long organizerId, EventRequest req) {
        validateTimes(req.startTime(), req.endTime());
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        Event event = new Event();
        event.setOrganizer(organizer);
        event.setStatus(EventStatus.DRAFT);
        apply(event, req);
        eventRepository.save(event);
        return assembler.toDetail(event, List.of());
    }

    @Transactional
    public EventDetailResponse update(Long organizerId, Long eventId, EventRequest req) {
        Event event = findOwned(organizerId, eventId);
        requireStatus(event, EventStatus.DRAFT, "Only DRAFT events can be edited");
        validateTimes(req.startTime(), req.endTime());

        List<TicketType> types = ticketTypeRepository.findByEventIdOrderByPriceAscIdAsc(eventId);
        // Dời giờ kết thúc sớm hơn thì không được để hạng vé nào còn bán sau đó
        if (types.stream().anyMatch(t -> t.getSaleEnd().isAfter(req.endTime()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SALE_AFTER_EVENT_END",
                    "Some ticket types are on sale after the new end time. Adjust them first");
        }
        apply(event, req);
        return assembler.toDetail(event, types);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> listMine(Long organizerId, EventStatus status, int page, int size) {
        Specification<Event> spec = Specification
                .where(EventSpecs.organizerIs(organizerId))
                .and(EventSpecs.hasStatus(status));
        var pageable = PageResponse.pageable(page, size, Sort.by("createdAt").descending());
        return PageResponse.from(assembler.toSummaryPage(eventRepository.findAll(spec, pageable)));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getMine(Long organizerId, Long eventId) {
        return assembler.toDetail(findOwned(organizerId, eventId));
    }

    /** DRAFT -> PENDING: gửi ADMIN duyệt. */
    @Transactional
    public EventDetailResponse submit(Long organizerId, Long eventId) {
        Event event = findOwned(organizerId, eventId);
        requireStatus(event, EventStatus.DRAFT, "Only DRAFT events can be submitted");

        List<TicketType> types = ticketTypeRepository.findByEventIdOrderByPriceAscIdAsc(eventId);
        if (types.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_TICKET_TYPES", "Add at least one ticket type before submitting");
        }
        if (!event.getStartTime().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_ALREADY_STARTED", "The event start time is in the past");
        }
        event.setStatus(EventStatus.PENDING);
        event.setRejectReason(null);
        return assembler.toDetail(event, types);
    }

    @Transactional
    public EventDetailResponse cancel(Long organizerId, Long eventId) {
        Event event = findOwned(organizerId, eventId);
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.ENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_EVENT_STATE", "This event can no longer be cancelled");
        }
        List<TicketType> types = ticketTypeRepository.findByEventIdOrderByPriceAscIdAsc(eventId);
        boolean hasSold = types.stream().anyMatch(t -> t.getAvailableQuantity() < t.getTotalQuantity());
        if (hasSold) {
            // Chưa có luồng hoàn tiền nên chặn, tránh hủy sự kiện mà người mua đã trả tiền
            throw new ApiException(HttpStatus.CONFLICT, "HAS_SOLD_TICKETS",
                    "Tickets have already been sold. Refund flow is required before cancelling");
        }
        event.setStatus(EventStatus.CANCELLED);
        return assembler.toDetail(event, types);
    }

    // ---------------- Ticket types ----------------

    @Transactional
    public TicketTypeResponse addTicketType(Long organizerId, Long eventId, TicketTypeRequest req) {
        Event event = findOwned(organizerId, eventId);
        requireStatus(event, EventStatus.DRAFT, "Ticket types can only be changed while the event is DRAFT");
        validateTicketType(event, req);

        String name = req.name().trim();
        if (ticketTypeRepository.existsByEventIdAndName(eventId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_TYPE_EXISTS", "A ticket type with this name already exists");
        }
        TicketType type = new TicketType();
        type.setEvent(event);
        applyTicketType(type, name, req);
        ticketTypeRepository.save(type);
        return assembler.toTicketType(type, false);
    }

    @Transactional
    public TicketTypeResponse updateTicketType(Long organizerId, Long eventId, Long typeId, TicketTypeRequest req) {
        Event event = findOwned(organizerId, eventId);
        requireStatus(event, EventStatus.DRAFT, "Ticket types can only be changed while the event is DRAFT");
        validateTicketType(event, req);

        TicketType type = ticketTypeRepository.findByIdAndEventId(typeId, eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found"));
        String name = req.name().trim();
        if (ticketTypeRepository.existsByEventIdAndNameAndIdNot(eventId, name, typeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_TYPE_EXISTS", "A ticket type with this name already exists");
        }
        applyTicketType(type, name, req);
        return assembler.toTicketType(type, false);
    }

    @Transactional
    public void deleteTicketType(Long organizerId, Long eventId, Long typeId) {
        Event event = findOwned(organizerId, eventId);
        requireStatus(event, EventStatus.DRAFT, "Ticket types can only be changed while the event is DRAFT");
        TicketType type = ticketTypeRepository.findByIdAndEventId(typeId, eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found"));
        ticketTypeRepository.delete(type);
    }

    // ---------------- helpers ----------------

    /** Sự kiện của người khác trả 404 chứ không phải 403, để không lộ việc nó có tồn tại. */
    private Event findOwned(Long organizerId, Long eventId) {
        return eventRepository.findByIdAndOrganizerId(eventId, organizerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
    }

    private void requireStatus(Event event, EventStatus expected, String message) {
        if (event.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_EVENT_STATE", message);
        }
    }

    private void validateTimes(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE", "End time must be after start time");
        }
    }

    private void validateTicketType(Event event, TicketTypeRequest req) {
        if (!req.saleEnd().isAfter(req.saleStart())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SALE_WINDOW", "Sale end must be after sale start");
        }
        if (req.saleEnd().isAfter(event.getEndTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SALE_AFTER_EVENT_END", "Sales cannot continue after the event ends");
        }
        int maxPerOrder = req.maxPerOrder() == null ? DEFAULT_MAX_PER_ORDER : req.maxPerOrder();
        if (maxPerOrder > req.totalQuantity()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MAX_PER_ORDER", "Max per order cannot exceed total quantity");
        }
    }

    private void apply(Event event, EventRequest req) {
        event.setTitle(req.title().trim());
        event.setDescription(req.description());
        event.setVenue(req.venue().trim());
        event.setCategory(req.category() == null ? EventCategory.OTHER : req.category());
        event.setBannerUrl(req.bannerUrl());
        event.setStartTime(req.startTime());
        event.setEndTime(req.endTime());
    }

    private void applyTicketType(TicketType type, String name, TicketTypeRequest req) {
        type.setName(name);
        type.setPrice(req.price());
        type.setTotalQuantity(req.totalQuantity());
        type.setAvailableQuantity(req.totalQuantity());   // DRAFT: chưa bán nên còn nguyên
        type.setSaleStart(req.saleStart());
        type.setSaleEnd(req.saleEnd());
        type.setMaxPerOrder(req.maxPerOrder() == null ? DEFAULT_MAX_PER_ORDER : req.maxPerOrder());
    }
}
