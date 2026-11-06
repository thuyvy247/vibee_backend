package com.vibee.service;

import com.vibee.config.OrderProperties;
import com.vibee.dto.CreateOrderRequest;
import com.vibee.dto.OrderResponse;
import com.vibee.dto.PageResponse;
import com.vibee.entity.Event;
import com.vibee.entity.Order;
import com.vibee.entity.OrderItem;
import com.vibee.entity.TicketType;
import com.vibee.enums.EventStatus;
import com.vibee.enums.OrderStatus;
import com.vibee.exception.ApiException;
import com.vibee.repository.OrderItemRepository;
import com.vibee.repository.OrderRepository;
import com.vibee.repository.TicketTypeRepository;
import com.vibee.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final OrderFulfillmentService fulfillment;
    private final OrderAssembler assembler;
    private final OrderProperties props;

    @PersistenceContext
    private EntityManager em;

    /**
     * Tạo đơn và giữ vé trong một transaction. Nếu bất kỳ hạng vé nào hết vé, mọi thay đổi bị rollback
     * nên không bao giờ giữ dở dang. Việc trừ kho là một câu UPDATE có điều kiện (xem StockService).
     */
    @Transactional
    public OrderResponse create(Long userId, CreateOrderRequest req) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (CreateOrderRequest.Item item : req.items()) {
            if (quantities.put(item.ticketTypeId(), item.quantity()) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_TICKET_TYPE",
                        "Each ticket type can only appear once in an order");
            }
        }

        // Chặn một người giữ chỗ hàng loạt rồi bỏ đó làm cạn kho
        if (orderRepository.countByUserIdAndStatus(userId, OrderStatus.PENDING) >= props.maxPendingPerUser()) {
            throw new ApiException(HttpStatus.CONFLICT, "TOO_MANY_PENDING_ORDERS",
                    "You have too many unpaid orders. Please pay or cancel one first");
        }

        List<TicketType> types = ticketTypeRepository.findAllWithEventByIdIn(quantities.keySet());
        if (types.size() != quantities.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found");
        }

        Event event = types.get(0).getEvent();
        Instant now = Instant.now();
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_NOT_ON_SALE", "This event is not on sale");
        }
        if (!now.isBefore(event.getEndTime())) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_ENDED", "This event has ended");
        }
        for (TicketType type : types) {
            if (!type.getEvent().getId().equals(event.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MIXED_EVENTS", "An order can only contain tickets of one event");
            }
            if (now.isBefore(type.getSaleStart())) {
                throw new ApiException(HttpStatus.CONFLICT, "SALE_NOT_STARTED", "Sales have not started for: " + type.getName());
            }
            if (now.isAfter(type.getSaleEnd())) {
                throw new ApiException(HttpStatus.CONFLICT, "SALE_ENDED", "Sales have ended for: " + type.getName());
            }
            if (quantities.get(type.getId()) > type.getMaxPerOrder()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "EXCEEDS_MAX_PER_ORDER",
                        "You can buy at most " + type.getMaxPerOrder() + " tickets of " + type.getName() + " per order");
            }
        }

        // Bước then chốt: trừ kho nguyên tử. (Câu UPDATE hàng loạt làm sạch persistence context,
        // nên bên dưới chỉ dùng dữ liệu đã đọc sẵn và các tham chiếu getReferenceById.)
        stockService.reserveOrFail(quantities);

        Order order = new Order();
        order.setOrderCode(CodeGenerator.orderCode());
        order.setUser(userRepository.getReferenceById(userId));
        order.setStatus(OrderStatus.PENDING);
        order.setExpiresAt(now.plus(Duration.ofMinutes(props.holdMinutes())));

        BigDecimal total = BigDecimal.ZERO;
        for (TicketType type : types) {
            int quantity = quantities.get(type.getId());
            OrderItem item = new OrderItem();
            item.setTicketType(ticketTypeRepository.getReferenceById(type.getId()));
            item.setQuantity(quantity);
            item.setUnitPrice(type.getPrice());   // chốt giá tại thời điểm mua
            order.addItem(item);
            total = total.add(type.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        order.setTotalAmount(total);
        orderRepository.save(order);

        // Đơn miễn phí: không cần thanh toán, phát vé luôn
        if (total.signum() == 0) {
            fulfillment.completeOrder(order.getId());
        }
        return reload(userId, order.getOrderCode());
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, String orderCode) {
        return assembler.toResponse(findOwned(userId, orderCode));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMine(Long userId, OrderStatus status, int page, int size) {
        var pageable = PageResponse.pageable(page, size, Sort.by("createdAt").descending());
        Page<Order> orders = status == null
                ? orderRepository.findByUserId(userId, pageable)
                : orderRepository.findByUserIdAndStatus(userId, status, pageable);
        return PageResponse.from(assembler.toPage(orders));
    }

    @Transactional
    public OrderResponse cancel(Long userId, String orderCode) {
        Order order = findOwned(userId, orderCode);
        var quantities = StockService.quantitiesOf(order.getItems());

        if (orderRepository.transition(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATE", "Only unpaid orders can be cancelled");
        }
        stockService.release(quantities);
        return reload(userId, orderCode);
    }

    /** Gọi bởi job quét đơn quá hạn. Mỗi đơn một transaction riêng nên một đơn lỗi không kéo theo đơn khác. */
    @Transactional
    public void expire(Long orderId) {
        // Thua cuộc đua với thanh toán/hủy thì thôi, đơn đó không còn PENDING
        if (orderRepository.transition(orderId, OrderStatus.PENDING, OrderStatus.EXPIRED) == 0) {
            return;
        }
        List<OrderItem> items = orderItemRepository.findByOrderIds(List.of(orderId));
        stockService.release(StockService.quantitiesOf(items));
    }

    private Order findOwned(Long userId, String orderCode) {
        return orderRepository.findByOrderCodeAndUserId(orderCode, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
    }

    /** Đọc lại đơn từ DB sau khi đã có các câu UPDATE hàng loạt, để response phản ánh đúng trạng thái mới. */
    private OrderResponse reload(Long userId, String orderCode) {
        em.flush();
        em.clear();
        return assembler.toResponse(findOwned(userId, orderCode));
    }
}
