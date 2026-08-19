package com.rhotels.dto;

/**
 * DTO đóng gói kết quả đầu ra của tool áp dụng voucher vào hóa đơn.
 */
public record ApplyVoucherResponse(
        boolean isSuccess,
        String invoiceId,
        String voucherCode,
        double discountPercent,
        double originalAmount,
        double finalAmount,
        String message) {

    public static ApplyVoucherResponse success(String invoiceId, String voucherCode,
                                                double discountPercent, double originalAmount,
                                                double finalAmount, String message) {
        return new ApplyVoucherResponse(true, invoiceId, voucherCode,
                discountPercent, originalAmount, finalAmount, message);
    }

    public static ApplyVoucherResponse error(String invoiceId, String message) {
        return new ApplyVoucherResponse(false, invoiceId, null, 0.0, 0.0, 0.0, message);
    }
}