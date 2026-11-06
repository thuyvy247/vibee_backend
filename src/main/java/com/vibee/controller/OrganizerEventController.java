package com.vibee.controller;

import com.vibee.dto.*;
import com.vibee.enums.EventStatus;
import com.vibee.security.UserPrincipal;
import com.vibee.service.OrganizerEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizer/events")
@PreAuthorize("hasRole('ORGANIZER')")
@RequiredArgsConstructor
public class OrganizerEventController {

    private final OrganizerEventService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetailResponse create(@AuthenticationPrincipal UserPrincipal me,
                                      @Valid @RequestBody EventRequest req) {
        return service.create(me.getId(), req);
    }

    @GetMapping
    public PageResponse<EventSummaryResponse> listMine(@AuthenticationPrincipal UserPrincipal me,
                                                       @RequestParam(required = false) EventStatus status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "12") int size) {
        return service.listMine(me.getId(), status, page, size);
    }

    @GetMapping("/{id}")
    public EventDetailResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return service.getMine(me.getId(), id);
    }

    @PutMapping("/{id}")
    public EventDetailResponse update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                      @Valid @RequestBody EventRequest req) {
        return service.update(me.getId(), id, req);
    }

    @PatchMapping("/{id}/submit")
    public EventDetailResponse submit(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return service.submit(me.getId(), id);
    }

    @PatchMapping("/{id}/cancel")
    public EventDetailResponse cancel(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return service.cancel(me.getId(), id);
    }

    @PostMapping("/{id}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse addTicketType(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                            @Valid @RequestBody TicketTypeRequest req) {
        return service.addTicketType(me.getId(), id, req);
    }

    @PutMapping("/{id}/ticket-types/{typeId}")
    public TicketTypeResponse updateTicketType(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                               @PathVariable Long typeId,
                                               @Valid @RequestBody TicketTypeRequest req) {
        return service.updateTicketType(me.getId(), id, typeId, req);
    }

    @DeleteMapping("/{id}/ticket-types/{typeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTicketType(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                 @PathVariable Long typeId) {
        service.deleteTicketType(me.getId(), id, typeId);
    }
}
