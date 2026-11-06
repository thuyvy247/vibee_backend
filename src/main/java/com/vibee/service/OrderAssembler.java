package com.vibee.service;

import com.vibee.dto.OrderResponse;
import com.vibee.entity.Event;
import com.vibee.entity.Order;
import com.vibee.entity.OrderItem;
import com.vibee.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderAssembler {

    private final OrderItemRepository orderItemRepository;

    public OrderResponse toResponse(Order order) {
        return toResponse(order, order.getItems());
    }

    /** 2 query cho cả trang đơn hàng (đơn + toàn bộ dòng hàng của chúng), không bị N+1. */
    public Page<OrderResponse> toPage(Page<Order> page) {
        List<Long> ids = page.getContent().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrder = ids.isEmpty() ? Map.of()
                : orderItemRepository.findByOrderIds(ids).stream()
                        .collect(Collectors.groupingBy(i -> i.getOrder().getId()));
        return page.map(o -> toResponse(o, itemsByOrder.getOrDefault(o.getId(), List.of())));
    }

    private OrderResponse toResponse(Order order, Collection<OrderItem> items) {
        List<OrderItem> sorted = items.stream()
                .sorted(Comparator.comparing(i -> i.getTicketType().getId()))
                .toList();
        // Một đơn luôn thuộc đúng một sự kiện
        Event event = sorted.get(0).getTicketType().getEvent();

        return new OrderResponse(
                order.getId(), order.getOrderCode(), order.getStatus(), order.getTotalAmount(),
                order.getExpiresAt(), order.getCreatedAt(),
                new OrderResponse.EventBrief(event.getId(), event.getTitle(), event.getVenue(),
                        event.getStartTime(), event.getBannerUrl()),
                sorted.stream()
                        .map(i -> new OrderResponse.Item(i.getTicketType().getId(), i.getTicketType().getName(),
                                i.getQuantity(), i.getUnitPrice()))
                        .toList());
    }
}
