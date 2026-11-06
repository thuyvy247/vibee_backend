package com.vibee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCreateRequest(@NotBlank @Size(max = 16) String orderCode) {}
