package com.vibee.service;

import com.vibee.config.VnPayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Tạo URL thanh toán VNPay (v2.1.0) và kiểm tra chữ ký HMAC-SHA512 của callback. */
@Component
@RequiredArgsConstructor
public class VnPayClient {

    private static final DateTimeFormatter VNPAY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final VnPayProperties props;

    public boolean isConfigured() {
        return notBlank(props.tmnCode()) && notBlank(props.hashSecret());
    }

    public String buildPaymentUrl(String txnRef, BigDecimal amount, String orderInfo, String ipAddress, Instant expireAt) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", props.tmnCode());
        // VNPay quy định số tiền nhân 100
        params.put("vnp_Amount", amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", props.returnUrl());
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_CreateDate", VNPAY_TIME.format(Instant.now()));
        params.put("vnp_ExpireDate", VNPAY_TIME.format(expireAt));

        String hashData = hashData(params);
        return props.payUrl() + "?" + hashData + "&vnp_SecureHash=" + hmacSha512(props.hashSecret(), hashData);
    }

    /** Chữ ký sai nghĩa là dữ liệu bị sửa trên đường đi hoặc không phải do VNPay gửi. */
    public boolean verify(Map<String, String> requestParams) {
        String received = requestParams.get("vnp_SecureHash");
        if (received == null || !isConfigured()) {
            return false;
        }
        Map<String, String> signed = new TreeMap<>();
        requestParams.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")) {
                signed.put(key, value);
            }
        });
        String expected = hmacSha512(props.hashSecret(), hashData(signed));
        // So sánh thời gian không đổi để không lộ thông tin qua độ trễ
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static String hashData(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> notBlank(e.getValue()))
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }

    private static String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot compute HMAC-SHA512", e);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
