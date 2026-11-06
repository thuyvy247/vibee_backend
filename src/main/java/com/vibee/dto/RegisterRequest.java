package com.vibee.dto;

import com.vibee.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt chỉ xử lý tối đa 72 byte đầu, nên chặn ở 72
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "Invalid phone number") String phone,
        Role role   // tùy chọn: ATTENDEE (mặc định) hoặc ORGANIZER
) {}
