package com.rhotels.dto;

/**
 * DTO đại diện cho một mã voucher của khách hàng.
 */
public record VoucherInfo(
        String voucherCode,
        double discountPercent,
        String description,
        boolean isActive) {
}