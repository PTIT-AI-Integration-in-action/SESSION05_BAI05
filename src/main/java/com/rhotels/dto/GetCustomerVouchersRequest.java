package com.rhotels.dto;

/**
 * DTO đóng gói tham số đầu vào cho tool tra cứu voucher khách hàng.
 */
public record GetCustomerVouchersRequest(
        String customerId) {

    public boolean isValid() {
        return customerId != null && !customerId.isBlank();
    }
}