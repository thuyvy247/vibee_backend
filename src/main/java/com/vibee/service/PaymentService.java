package com.vibee.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibee.entity.Order;
import com.vibee.entity.Payment;
import com.vibee.enums.OrderStatus;
import com.vibee.enums.PaymentStatus;
import com.vibee.exception.ApiException;
import com.vibee.repository.OrderRepository;
import com.vibee.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    public enum Code { CONFIRMED, ALREADY_CONFIRMED, FAILED, NOT_FOUND, AMOUNT_MISMATCH, INVALID_SIGNATURE }

    public record VnPayResult(Code code, String orderCode) {}

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderFulfillmentService fulfillment;
    private final VnPayClient vnPayClient;
    private final ObjectMapper objectMapper;

    @Value("${app.payment.mock-enabled:false}")
    private boolean mockEnabled;

    // ---------------- Tạo thanh toán ----------------

    @Transactional
    public String createVnPayUrl(Long userId, String orderCode, String clientIp) {
        if (!vnPayClient.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED",
                    "Online payment is not configured on this server");
        }
        Order order = requirePayableOrder(userId, orderCode);
        Payment payment = newPayment(order, "VNPAY", "P");
        return vnPayClient.buildPaymentUrl(payment.getTxnRef(), order.getTotalAmount(),
                "Thanh toan ve Vibee " + orderCode, clientIp, order.getExpiresAt());
    }

    /** Chỉ để thử nghiệm khi chưa có tài khoản VNPay sandbox. Tắt mặc định, bật bằng PAYMENT_MOCK_ENABLED=true. */
    @Transactional
    public void confirmMock(Long userId, String orderCode) {
        if (!mockEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
        }
        Order order = requirePayableOrder(userId, orderCode);
        Payment payment = newPayment(order, "MOCK", "M");
        confirmSuccess(payment, "MOCK-" + payment.getId(), "{\"mock\":true}");
    }

    // ---------------- Xử lý callback VNPay (dùng chung cho return URL và IPN) ----------------

    @Transactional
    public VnPayResult processVnPay(Map<String, String> params) {
        if (!vnPayClient.verify(params)) {
            return new VnPayResult(Code.INVALID_SIGNATURE, null);
        }
        String txnRef = params.get("vnp_TxnRef");
        Payment payment = txnRef == null ? null : paymentRepository.findByTxnRef(txnRef).orElse(null);
        if (payment == null) {
            return new VnPayResult(Code.NOT_FOUND, null);
        }
        String orderCode = payment.getOrder().getOrderCode();

        // Đối chiếu số tiền với dữ liệu của MÌNH, không tin số tiền client gửi lên
        BigDecimal paid;
        try {
            paid = new BigDecimal(params.getOrDefault("vnp_Amount", "")).movePointLeft(2);
        } catch (NumberFormatException e) {
            return new VnPayResult(Code.AMOUNT_MISMATCH, orderCode);
        }
        if (payment.getAmount().compareTo(paid) != 0) {
            return new VnPayResult(Code.AMOUNT_MISMATCH, orderCode);
        }

        String raw = toJson(params);
        boolean success = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
        if (!success) {
            paymentRepository.markFailed(payment.getId(), raw);
            return new VnPayResult(Code.FAILED, orderCode);
        }
        return new VnPayResult(confirmSuccess(payment, params.get("vnp_TransactionNo"), raw), orderCode);
    }

    // ---------------- helpers ----------------

    /**
     * Idempotent: VNPay gọi IPN lặp lại, người dùng F5 trang kết quả... thì vé vẫn chỉ phát một lần,
     * vì chỉ lần đầu tiên chuyển được payment từ PENDING sang SUCCESS.
     */
    private Code confirmSuccess(Payment payment, String providerTxnNo, String raw) {
        if (paymentRepository.markSuccess(payment.getId(), providerTxnNo, raw, Instant.now()) == 0) {
            return Code.ALREADY_CONFIRMED;
        }
        if (fulfillment.completeOrder(payment.getOrder().getId()) == OrderFulfillmentService.Outcome.REFUND_REQUIRED) {
            paymentRepository.setStatus(payment.getId(), PaymentStatus.REFUND_REQUIRED);
        }
        return Code.CONFIRMED;
    }

    private Order requirePayableOrder(Long userId, String orderCode) {
        Order order = orderRepository.findByOrderCodeAndUserId(orderCode, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATE", "This order cannot be paid");
        }
        if (!order.getExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_EXPIRED", "This order has expired");
        }
        if (order.getTotalAmount().signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "NOTHING_TO_PAY", "This order does not require payment");
        }
        return order;
    }

    /** Mỗi lần bấm thanh toán là một giao dịch mới (txnRef riêng), vì VNPay không cho dùng lại txnRef đã thất bại. */
    private Payment newPayment(Order order, String provider, String prefix) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(provider);
        payment.setTxnRef(order.getOrderCode() + prefix + (paymentRepository.countByOrderId(order.getId()) + 1));
        payment.setAmount(order.getTotalAmount());
        return paymentRepository.save(payment);
    }

    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return params.toString();
        }
    }
}
