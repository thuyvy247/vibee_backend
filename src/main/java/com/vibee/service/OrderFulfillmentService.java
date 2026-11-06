package com.vibee.service;

import com.vibee.entity.Event;
import com.vibee.entity.Order;
import com.vibee.entity.OrderItem;
import com.vibee.entity.Ticket;
import com.vibee.enums.OrderStatus;
import com.vibee.repository.OrderRepository;
import com.vibee.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Hoàn tất đơn đã được thanh toán: chuyển PAID và phát vé. Dùng chung cho VNPay, mock và đơn miễn phí. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {

    public enum Outcome { PAID, ALREADY_PAID, REFUND_REQUIRED }

    private record Line(Long orderItemId, Long eventId, int quantity) {}

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final StockService stockService;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public Outcome completeOrder(Long orderId) {
        Order order = orderRepository.findDetailById(orderId).orElseThrow();

        // Chụp dữ liệu cần dùng trước: các câu UPDATE hàng loạt bên dưới sẽ làm sạch persistence context
        List<Line> lines = order.getItems().stream()
                .map(i -> new Line(i.getId(), i.getTicketType().getEvent().getId(), i.getQuantity()))
                .toList();
        Map<Long, Integer> quantities = StockService.quantitiesOf(order.getItems());

        // Trường hợp bình thường: đơn còn PENDING, kho vẫn đang giữ vé cho đơn này
        if (orderRepository.transition(orderId, OrderStatus.PENDING, OrderStatus.PAID) == 1) {
            issueTickets(lines);
            return Outcome.PAID;
        }

        OrderStatus current = orderRepository.findStatusById(orderId).orElseThrow();
        if (current == OrderStatus.PAID) {
            return Outcome.ALREADY_PAID;   // gọi lặp lại: không phát vé lần hai
        }

        // Thanh toán đến muộn: đơn đã EXPIRED/CANCELLED và vé đã trả về kho. Thử giữ lại vé cho khách đã trả tiền.
        if (!stockService.tryReserve(quantities)) {
            log.error("Late payment for order {} but tickets are sold out: manual refund required", orderId);
            return Outcome.REFUND_REQUIRED;
        }
        if (orderRepository.transition(orderId, current, OrderStatus.PAID) == 0) {
            stockService.release(quantities);
            log.error("Order {} changed state during late payment: manual refund required", orderId);
            return Outcome.REFUND_REQUIRED;
        }
        issueTickets(lines);
        return Outcome.PAID;
    }

    private void issueTickets(List<Line> lines) {
        List<Ticket> tickets = new ArrayList<>();
        for (Line line : lines) {
            for (int i = 0; i < line.quantity(); i++) {
                Ticket ticket = new Ticket();
                ticket.setOrderItem(em.getReference(OrderItem.class, line.orderItemId()));
                ticket.setEvent(em.getReference(Event.class, line.eventId()));
                ticket.setCode(CodeGenerator.ticketCode());
                tickets.add(ticket);
            }
        }
        ticketRepository.saveAll(tickets);
    }
}
