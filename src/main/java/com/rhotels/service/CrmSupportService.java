package com.rhotels.service;

import com.rhotels.dto.ApplyVoucherResponse;
import com.rhotels.dto.GetCustomerVouchersRequest;
import com.rhotels.dto.GetCustomerVouchersResponse;
import com.rhotels.dto.VoucherInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * CRM Support Agent - chăm sóc khách hàng và tra cứu voucher.
 * Hai tool được đánh dấu @Tool, Spring AI sẽ tự động đăng ký và cho LLM gọi.
 * Thiết kế phòng thủ: mọi lỗi nghiệp vụ đều trả về response có isSuccess=false
 * thay vì ném exception làm crash ứng dụng.
 */
@Service
public class CrmSupportService {

    // Giả lập: danh sách voucher của khách hàng 888 (từ DB CRM)
    private static final Map<String, List<VoucherInfo>> CUSTOMER_VOUCHERS = Map.of(
            "KH888", List.of(
                    new VoucherInfo("VIP20", 20.0, "Giảm 20% cho khách VIP", true),
                    new VoucherInfo("WELCOME10", 10.0, "Giảm 10% khách mới", true),
                    new VoucherInfo("EXPIRE5", 5.0, "Mã hết hạn", false)
            ),
            "KH999", List.of(
                    new VoucherInfo("SALE15", 15.0, "Giảm 15% mùa hè", true)
            )
    );

    // Giả lập trạng thái hóa đơn trong database hệ thống lữ hành
    // key = invoiceId, value = [đã thanh toán chưa, số tiền gốc, voucher đã apply]
    private static final Map<String, List<Object>> INVOICES = Map.of(
            "HD999", List.of(false, 2000000.0, "NONE")
    );

    /**
     * Tool 1: Tra cứu danh sách voucher còn hạn của khách hàng.
     */
    @Tool(description = "Truy vấn hệ thống CRM lấy danh sách các mã giảm giá (voucher) còn hạn sử dụng "
            + "của một khách hàng dựa trên customerId. Trả về danh sách voucher kèm percent giảm giá.")
    public GetCustomerVouchersResponse getCustomerVouchers(GetCustomerVouchersRequest request) {

        // Phòng thủ: kiểm tra null
        if (request == null || !request.isValid()) {
            return GetCustomerVouchersResponse.error(
                    "Thiếu hoặc sai mã khách hàng (customerId). Vui lòng cung cấp mã khách hàng để tra cứu voucher.");
        }

        String customerId = request.customerId().trim().toUpperCase();
        List<VoucherInfo> vouchers = CUSTOMER_VOUCHERS.getOrDefault(customerId, List.of());

        if (vouchers.isEmpty()) {
            return GetCustomerVouchersResponse.error(
                    "Không tìm thấy mã khách hàng " + customerId + " trong hệ thống CRM. Vui lòng kiểm tra lại mã khách hàng.");
        }

        return GetCustomerVouchersResponse.success(customerId, vouchers);
    }

    /**
     * Tool 2: Áp dụng voucher vào hóa đơn. Có kiểm tra phòng thủ: hóa đơn đã thanh toán
     * hoặc voucher hết hạn sẽ trả về thông điệp lỗi nghiệp vụ thay vì ném exception.
     */
    @Tool(description = "Áp dụng một mã voucher vào hóa đơn (invoiceId) để cập nhật tổng số tiền phải trả. "
            + "Trả về thành công kèm số tiền gốc, tổng tiền sau giảm giá. Nếu hóa đơn đã thanh toán "
            + "hoặc voucher hết hạn sẽ trả về lỗi nghiệp vụ an toàn.")
    public ApplyVoucherResponse applyVoucherToInvoice(String invoiceId, String voucherCode) {

        // Phòng thủ: kiểm tra null/blank
        if (invoiceId == null || invoiceId.isBlank()) {
            return ApplyVoucherResponse.error(null, "Thiếu mã hóa đơn (invoiceId) cần áp dụng voucher.");
        }
        if (voucherCode == null || voucherCode.isBlank()) {
            return ApplyVoucherResponse.error(invoiceId, "Thiếu mã voucher (voucherCode) cần áp dụng vào hóa đơn " + invoiceId + ".");
        }

        String normalizedInvoice = invoiceId.trim().toUpperCase();
        String normalizedVoucher = voucherCode.trim().toUpperCase();

        // Kiểm tra hóa đơn có tồn tại trong DB không
        List<Object> invoice = INVOICES.get(normalizedInvoice);
        if (invoice == null) {
            return ApplyVoucherResponse.error(normalizedInvoice,
                    "Không tìm thấy hóa đơn " + normalizedInvoice + " trong hệ thống. Vui lòng kiểm tra lại mã hóa đơn.");
        }

        boolean isPaid = (boolean) invoice.get(0);
        double originalAmount = ((Number) invoice.get(1)).doubleValue();
        String appliedVoucher = (String) invoice.get(2);

        // Phòng thủ nghiệp vụ: hóa đơn đã thanh toán thì không được áp dụng voucher nữa
        if (isPaid) {
            return ApplyVoucherResponse.error(normalizedInvoice,
                    "Hóa đơn " + normalizedInvoice + " đã được thanh toán trước đó, không thể áp dụng thêm voucher. Không thể hoàn tất yêu cầu.");
        }

        // Phòng thủ nghiệp vụ: hóa đơn đã có voucher rồi
        if (!"NONE".equals(appliedVoucher)) {
            return ApplyVoucherResponse.error(normalizedInvoice,
                    "Hóa đơn " + normalizedInvoice + " đã được áp dụng voucher " + appliedVoucher + " trước đó. Không thể áp dụng thêm voucher khác.");
        }

        // Tra cứu voucher trong CRM để xác minh tồn tại và còn hạn
        VoucherInfo voucher = findVoucher(normalizedVoucher);
        if (voucher == null) {
            return ApplyVoucherResponse.error(normalizedInvoice,
                    "Mã voucher " + normalizedVoucher + " không tồn tại hoặc không thuộc khách hàng của hóa đơn này.");
        }
        if (!voucher.isActive()) {
            return ApplyVoucherResponse.error(normalizedInvoice,
                    "Mã voucher " + normalizedVoucher + " (" + voucher.description() + ") đã hết hạn sử dụng, không thể áp dụng.");
        }

        // Tính toán số tiền sau giảm giá
        double discount = originalAmount * voucher.discountPercent() / 100.0;
        double finalAmount = originalAmount - discount;

        // Giả lập lưu vào database (trong thực tế gọi invoiceRepository.save(...))
        // TODO: thay bằng update DB thật khi tích hợp repository
        List<Object> updated = List.of(false, originalAmount, normalizedVoucher);
        // INVOICES.put(normalizedInvoice, updated); // bản đầy đủ sẽ cập nhật DB tại đây

        return ApplyVoucherResponse.success(normalizedInvoice, normalizedVoucher,
                voucher.discountPercent(), originalAmount, finalAmount,
                String.format("Đã áp dụng voucher %s (-%.0f%%) thành công vào hóa đơn %s. "
                        + "Tổng tiền từ %,.0f VND còn %,.0f VND", normalizedVoucher,
                        voucher.discountPercent(), normalizedInvoice, originalAmount, finalAmount));
    }

    /**
     * Tìm voucher theo mã trong toàn bộ CRM (giả lập quét tất cả khách hàng).
     */
    private VoucherInfo findVoucher(String voucherCode) {
        return CUSTOMER_VOUCHERS.values().stream()
                .flatMap(List::stream)
                .filter(v -> v.voucherCode().equalsIgnoreCase(voucherCode))
                .findFirst()
                .orElse(null);
    }
}