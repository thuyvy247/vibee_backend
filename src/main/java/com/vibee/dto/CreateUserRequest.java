package com.vibee.dto;

import com.vibee.enums.Role;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "Invalid phone number") String phone,
        @NotNull Role role
) {}
