package com.rhotels.dto;

import java.util.List;

/**
 * DTO đóng gói kết quả đầu ra của tool tra cứu voucher.
 * Chứa cờ isSuccess để phòng thủ, không ném exception làm crash ứng dụng.
 */
public record GetCustomerVouchersResponse(
        boolean isSuccess,
        String customerId,
        List<VoucherInfo> vouchers,
        String message) {

    public static GetCustomerVouchersResponse success(String customerId, List<VoucherInfo> vouchers) {
        return new GetCustomerVouchersResponse(true, customerId, vouchers, "Truy vấn voucher thành công.");
    }

    public static GetCustomerVouchersResponse error(String message) {
        return new GetCustomerVouchersResponse(false, null, List.of(), message);
    }
}