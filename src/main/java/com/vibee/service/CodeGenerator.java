package com.vibee.service;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class CodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    // Bỏ các ký tự dễ nhầm: 0/O, 1/I
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private CodeGenerator() {}

    /** Mã đơn 16 ký tự, ví dụ VB7K2Q9XM4T8ZP3A. Không đoán được, dùng được làm vnp_TxnRef. */
    public static String orderCode() {
        StringBuilder sb = new StringBuilder("VB");
        for (int i = 0; i < 14; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Mã vé 128-bit ngẫu nhiên (32 ký tự hex) nằm trong mã QR: không thể đoán hay làm giả. */
    public static String ticketCode() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
