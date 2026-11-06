package com.vibee.controller;

import com.vibee.dto.CreateOrderRequest;
import com.vibee.dto.OrderResponse;
import com.vibee.dto.PageResponse;
import com.vibee.enums.OrderStatus;
import com.vibee.security.UserPrincipal;
import com.vibee.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody CreateOrderRequest req) {
        return orderService.create(me.getId(), req);
    }

    @GetMapping("/me")
    public PageResponse<OrderResponse> listMine(@AuthenticationPrincipal UserPrincipal me,
                                                @RequestParam(required = false) OrderStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return orderService.listMine(me.getId(), status, page, size);
    }

    // Dùng orderCode (không đoán được) thay vì id tự tăng
    @GetMapping("/{orderCode}")
    public OrderResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable String orderCode) {
        return orderService.get(me.getId(), orderCode);
    }

    @PostMapping("/{orderCode}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal UserPrincipal me, @PathVariable String orderCode) {
        return orderService.cancel(me.getId(), orderCode);
    }
}
