package com.example.billingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** 精算書の永続化。 */
@Mapper
public interface InvoiceMapper {

    String COLUMNS = """
            id, invoice_number, booking_id, shipper_id, shipper_name, shipper_corporate,
            leg_count, leg_factor, leg_region, weight_kg, cargo_type,
            base_amount_value, base_amount_currency,
            discount_rate, discount_amount_value, discount_amount_currency,
            cancellation_fee_value, cancellation_fee_currency, cancellation_fee_rate,
            booking_status_at_cancel,
            tax_rate, tax_amount, tax_exempt, total_amount_value, total_amount_currency,
            payment_status, issued_at
            """;

    /**
     * 精算書を書く。
     *
     * <p><strong>更新は用意しない</strong>（[ADR-027] 決定 4）。発行した精算書の金額は
     * 動かない。訂正は US23（IT12）で「取り消して出し直す」形にする。
     */
    @Insert("""
            INSERT INTO invoice (
                invoice_number, booking_id, shipper_id, shipper_name, shipper_corporate,
                leg_count, leg_factor, leg_region, weight_kg, cargo_type,
                base_amount_value, base_amount_currency,
                discount_rate, discount_amount_value, discount_amount_currency,
                cancellation_fee_value, cancellation_fee_currency, cancellation_fee_rate,
                booking_status_at_cancel,
                tax_rate, tax_amount, tax_exempt, total_amount_value, total_amount_currency,
                payment_status, issued_at)
            VALUES (
                #{invoiceNumber}, #{bookingId}, #{shipperId}, #{shipperName},
                #{shipperCorporate}, #{legCount}, #{legFactor}, #{legRegion},
                #{weightKg}, #{cargoType},
                #{baseAmountValue}, #{baseAmountCurrency},
                #{discountRate}, #{discountAmountValue}, #{discountAmountCurrency},
                #{cancellationFeeValue}, #{cancellationFeeCurrency}, #{cancellationFeeRate},
                #{bookingStatusAtCancel},
                #{taxRate}, #{taxAmount}, #{taxExempt},
                #{totalAmountValue}, #{totalAmountCurrency},
                #{paymentStatus}, #{issuedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(InvoiceRecord row);

    @Select("SELECT " + COLUMNS + " FROM invoice WHERE invoice_number = #{invoiceNumber}")
    @Results(id = "invoice", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "invoice_number", property = "invoiceNumber"),
            @Result(column = "booking_id", property = "bookingId"),
            @Result(column = "shipper_id", property = "shipperId"),
            @Result(column = "shipper_name", property = "shipperName"),
            @Result(column = "shipper_corporate", property = "shipperCorporate"),
            @Result(column = "leg_count", property = "legCount"),
            @Result(column = "leg_factor", property = "legFactor"),
            @Result(column = "leg_region", property = "legRegion"),
            @Result(column = "weight_kg", property = "weightKg"),
            @Result(column = "cargo_type", property = "cargoType"),
            @Result(column = "base_amount_value", property = "baseAmountValue"),
            @Result(column = "base_amount_currency", property = "baseAmountCurrency"),
            @Result(column = "discount_rate", property = "discountRate"),
            @Result(column = "discount_amount_value", property = "discountAmountValue"),
            @Result(column = "discount_amount_currency", property = "discountAmountCurrency"),
            @Result(column = "cancellation_fee_value", property = "cancellationFeeValue"),
            @Result(column = "cancellation_fee_currency", property = "cancellationFeeCurrency"),
            @Result(column = "cancellation_fee_rate", property = "cancellationFeeRate"),
            @Result(column = "booking_status_at_cancel", property = "bookingStatusAtCancel"),
            @Result(column = "tax_rate", property = "taxRate"),
            @Result(column = "tax_exempt", property = "taxExempt"),
            @Result(column = "tax_amount", property = "taxAmount"),
            @Result(column = "total_amount_value", property = "totalAmountValue"),
            @Result(column = "total_amount_currency", property = "totalAmountCurrency"),
            @Result(column = "payment_status", property = "paymentStatus"),
            @Result(column = "issued_at", property = "issuedAt"),
    })
    InvoiceRecord selectByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);

    /** **新しい順に並べる。** 発行済みの一覧は「最近出したもの」から見る。 */
    @Select("SELECT " + COLUMNS + " FROM invoice ORDER BY issued_at DESC, id DESC")
    @ResultMap("invoice")
    List<InvoiceRecord> selectAll();

    /** その予約に精算書があるか（決定 4——二重請求を防ぐ）。 */
    @Select("SELECT COUNT(*) FROM invoice WHERE booking_id = #{bookingId}")
    int countByBookingId(@Param("bookingId") String bookingId);

    /**
     * 請求番号を採番する（[ADR-011] と同じ形）。
     *
     * <p><strong>DB のシーケンスに任せる。</strong>MAX+1 の自前採番は、同時に 2 件発行
     * されたときに衝突する。
     */
    @Select("SELECT NEXTVAL('invoice_number_seq')")
    long nextInvoiceNumber();
}
