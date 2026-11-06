package com.vibee.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public record TicketTypeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal price,
        @NotNull @Min(1) @Max(1_000_000) Integer totalQuantity,
        @NotNull Instant saleStart,
        @NotNull Instant saleEnd,
        @Min(1) Integer maxPerOrder   // bỏ trống = 5
) {}
