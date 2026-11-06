package com.vibee.controller;

import com.vibee.dto.CheckInRequest;
import com.vibee.dto.CheckInResponse;
import com.vibee.security.UserPrincipal;
import com.vibee.service.CheckInService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasAnyRole('STAFF','ADMIN')")
@RequiredArgsConstructor
public class StaffController {

    private final CheckInService checkInService;

    @PostMapping("/check-in")
    public CheckInResponse checkIn(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody CheckInRequest req) {
        return checkInService.checkIn(me.getId(), req.code(), req.eventId());
    }
}
