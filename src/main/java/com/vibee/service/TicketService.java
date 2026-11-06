package com.vibee.service;

import com.vibee.dto.PageResponse;
import com.vibee.dto.TicketResponse;
import com.vibee.entity.Event;
import com.vibee.entity.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final com.vibee.repository.TicketRepository ticketRepository;

    public PageResponse<TicketResponse> listMine(Long userId, int page, int size) {
        // Sự kiện gần đây nhất lên trước
        var sort = Sort.by("event.startTime").descending().and(Sort.by("id").descending());
        var pageable = PageResponse.pageable(page, size, sort);
        return PageResponse.from(ticketRepository.findByOwner(userId, pageable).map(this::toResponse));
    }

    private TicketResponse toResponse(Ticket ticket) {
        Event e = ticket.getEvent();
        return new TicketResponse(
                ticket.getId(), ticket.getCode(), ticket.getStatus(), ticket.getCheckedInAt(),
                ticket.getOrderItem().getTicketType().getName(),
                ticket.getOrderItem().getOrder().getOrderCode(),
                new TicketResponse.EventBrief(e.getId(), e.getTitle(), e.getVenue(), e.getStartTime(), e.getEndTime(), e.getBannerUrl()));
    }
}
