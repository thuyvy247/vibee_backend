package com.vibee.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty @Size(max = 10) @Valid List<Item> items
) {
    public record Item(
            @NotNull Long ticketTypeId,
            @NotNull @Min(1) @Max(100) Integer quantity
    ) {}
}
