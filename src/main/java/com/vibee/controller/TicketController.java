package com.vibee.controller;

import com.vibee.dto.PageResponse;
import com.vibee.dto.TicketResponse;
import com.vibee.security.UserPrincipal;
import com.vibee.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/me")
    public PageResponse<TicketResponse> mine(@AuthenticationPrincipal UserPrincipal me,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return ticketService.listMine(me.getId(), page, size);
    }
}
