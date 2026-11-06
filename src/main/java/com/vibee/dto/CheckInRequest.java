package com.vibee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckInRequest(
        @NotBlank @Size(max = 64) String code,
        Long eventId   // tùy chọn: nếu gửi, vé của sự kiện khác sẽ bị từ chối
) {}
