package com.vibee.service;

import com.vibee.entity.OrderItem;
import com.vibee.exception.ApiException;
import com.vibee.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Giữ/trả tồn kho. Luôn duyệt theo thứ tự ticketTypeId tăng dần: hai đơn cùng mua nhiều hạng vé
 * sẽ khóa các dòng theo cùng một thứ tự nên không thể bế tắc (deadlock) với nhau.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final TicketTypeRepository ticketTypeRepository;

    public static Map<Long, Integer> quantitiesOf(Collection<OrderItem> items) {
        Map<Long, Integer> quantities = new TreeMap<>();
        items.forEach(i -> quantities.merge(i.getTicketType().getId(), i.getQuantity(), Integer::sum));
        return quantities;
    }

    /** Giữ vé cho tất cả hạng vé hoặc không giữ gì cả. Trả false nếu một hạng không đủ vé. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryReserve(Map<Long, Integer> quantities) {
        List<Map.Entry<Long, Integer>> reserved = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : new TreeMap<>(quantities).entrySet()) {
            if (ticketTypeRepository.decreaseStock(entry.getKey(), entry.getValue()) == 0) {
                // Hoàn lại những hạng đã giữ được trước đó
                reserved.forEach(r -> ticketTypeRepository.increaseStock(r.getKey(), r.getValue()));
                return false;
            }
            reserved.add(entry);
        }
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveOrFail(Map<Long, Integer> quantities) {
        if (!tryReserve(quantities)) {
            throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT",
                    "Not enough tickets available for one or more selected ticket types");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Map<Long, Integer> quantities) {
        new TreeMap<>(quantities).forEach(ticketTypeRepository::increaseStock);
    }
}
