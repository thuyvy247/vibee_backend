package com.vibee.controller;

import com.vibee.dto.PaymentCreateRequest;
import com.vibee.dto.PaymentUrlResponse;
import com.vibee.security.UserPrincipal;
import com.vibee.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/vnpay/create")
    public PaymentUrlResponse create(@AuthenticationPrincipal UserPrincipal me,
                                     @Valid @RequestBody PaymentCreateRequest req,
                                     HttpServletRequest http) {
        return new PaymentUrlResponse(paymentService.createVnPayUrl(me.getId(), req.orderCode(), clientIp(http)));
    }

    /** Trình duyệt người dùng được VNPay chuyển về đây. Xử lý xong thì chuyển tiếp sang trang kết quả của frontend. */
    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        PaymentService.VnPayResult result = paymentService.processVnPay(params);
        String status = switch (result.code()) {
            case CONFIRMED, ALREADY_CONFIRMED -> "success";
            case FAILED -> "failed";
            default -> "invalid";
        };
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(frontendUrl + "/payment/result")
                .queryParam("status", status);
        if (result.orderCode() != null) {
            target.queryParam("orderCode", result.orderCode());
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target.build().toUriString())).build();
    }

    /** VNPay (server) gọi về. Cần URL công khai (ngrok khi chạy local). Phản hồi theo đúng đặc tả của VNPay. */
    @GetMapping("/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        try {
            return switch (paymentService.processVnPay(params).code()) {
                case CONFIRMED, FAILED -> ipn("00", "Confirm Success");
                case ALREADY_CONFIRMED -> ipn("02", "Order already confirmed");
                case NOT_FOUND -> ipn("01", "Order not found");
                case AMOUNT_MISMATCH -> ipn("04", "Invalid amount");
                case INVALID_SIGNATURE -> ipn("97", "Invalid signature");
            };
        } catch (RuntimeException e) {
            log.error("VNPay IPN failed", e);
            return ipn("99", "Unknown error");
        }
    }

    /** Giả lập thanh toán thành công (chỉ chạy khi PAYMENT_MOCK_ENABLED=true). */
    @PostMapping("/mock/{orderCode}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mockConfirm(@AuthenticationPrincipal UserPrincipal me, @PathVariable String orderCode) {
        paymentService.confirmMock(me.getId(), orderCode);
    }

    private static Map<String, String> ipn(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("RspCode", code);
        body.put("Message", message);
        return body;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip.contains(":") ? "127.0.0.1" : ip;   // VNPay không nhận IPv6
    }
}
