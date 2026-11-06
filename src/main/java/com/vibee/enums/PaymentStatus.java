package com.vibee.enums;

public enum PaymentStatus {
    PENDING, SUCCESS, FAILED,
    REFUND_REQUIRED   // khách đã trả tiền nhưng đơn đã hết hạn và vé đã bán hết: cần hoàn tiền thủ công
}
