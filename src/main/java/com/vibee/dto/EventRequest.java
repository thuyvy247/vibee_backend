package com.vibee.dto;

import com.vibee.enums.EventCategory;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

/** Thời gian theo ISO-8601 UTC, ví dụ 2026-12-20T12:00:00Z. */
public record EventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 10000) String description,
        @NotBlank @Size(max = 255) String venue,
        EventCategory category,   // bỏ trống = OTHER
        @URL @Size(max = 500) String bannerUrl,
        @NotNull @Future Instant startTime,
        @NotNull Instant endTime
) {}
