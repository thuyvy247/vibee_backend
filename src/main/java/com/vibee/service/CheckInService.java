package com.vibee.service;

import com.vibee.dto.CheckInResponse;
import com.vibee.entity.Ticket;
import com.vibee.enums.TicketStatus;
import com.vibee.exception.ApiException;
import com.vibee.repository.TicketRepository;
import com.vibee.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CheckInService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Transactional
    public CheckInResponse checkIn(Long staffId, String code, Long eventId) {
        Ticket ticket = ticketRepository.findByCode(code.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket not found"));

        if (eventId != null && !ticket.getEvent().getId().equals(eventId)) {
            throw new ApiException(HttpStatus.CONFLICT, "WRONG_EVENT", "This ticket is for a different event");
        }

        // Một câu UPDATE có điều kiện thay cho "đọc rồi sửa": hai cổng quét cùng một vé cùng lúc thì chỉ một cổng được vào
        Instant now = Instant.now();
        if (ticketRepository.markUsed(ticket.getCode(), now, userRepository.getReferenceById(staffId)) == 0) {
            TicketStatus status = ticketRepository.findStatusByCode(ticket.getCode()).orElse(ticket.getStatus());
            if (status == TicketStatus.USED) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_USED", "This ticket has already been used");
            }
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_CANCELLED", "This ticket is no longer valid");
        }

        return new CheckInResponse(
                ticket.getCode(),
                ticket.getEvent().getTitle(),
                ticket.getOrderItem().getTicketType().getName(),
                ticket.getOrderItem().getOrder().getUser().getFullName(),
                now);
    }
}
