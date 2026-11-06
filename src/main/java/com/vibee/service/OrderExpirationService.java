package com.vibee.service;

import com.vibee.enums.OrderStatus;
import com.vibee.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Quét các đơn PENDING quá hạn giữ chỗ, chuyển sang EXPIRED và hoàn vé về kho.
 * Chạy trên nhiều instance cùng lúc vẫn an toàn nhờ OrderService.expire dùng UPDATE có điều kiện.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${app.order.expire-scan-ms:30000}")
    public void expireOverdueOrders() {
        List<Long> ids = orderRepository.findExpiredIds(OrderStatus.PENDING, Instant.now(), PageRequest.of(0, BATCH_SIZE));
        for (Long id : ids) {
            try {
                orderService.expire(id);
            } catch (RuntimeException e) {
                log.error("Failed to expire order {}", id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("Processed {} overdue order(s)", ids.size());
        }
    }
}
