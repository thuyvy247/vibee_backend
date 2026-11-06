package com.vibee.controller;

import com.vibee.dto.EventDetailResponse;
import com.vibee.dto.EventSummaryResponse;
import com.vibee.dto.PageResponse;
import com.vibee.dto.RejectRequest;
import com.vibee.enums.EventStatus;
import com.vibee.service.AdminEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/events")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminEventController {

    private final AdminEventService service;

    /** Mặc định trả hàng đợi chờ duyệt (PENDING). */
    @GetMapping
    public PageResponse<EventSummaryResponse> list(@RequestParam(defaultValue = "PENDING") EventStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "12") int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/{id}")
    public EventDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/approve")
    public EventDetailResponse approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PatchMapping("/{id}/reject")
    public EventDetailResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return service.reject(id, req.reason());
    }
}
