package com.example.billingms.infrastructure.repositories;

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
            i.id, i.invoice_number, i.booking_id, i.shipper_id, i.shipper_name, i.shipper_corporate,
            i.simulated,
            i.leg_count, i.leg_factor, i.leg_region, i.weight_kg, i.cargo_type,
            i.base_amount_value, i.base_amount_currency,
            i.discount_rate, i.discount_amount_value, i.discount_amount_currency,
            i.cancellation_fee_value, i.cancellation_fee_currency, i.cancellation_fee_rate,
            i.booking_status_at_cancel,
            i.tax_rate, i.tax_amount, i.tax_exempt, i.total_amount_value, i.total_amount_currency,
            i.payment_status, i.issued_at, i.due_date, i.voided_at, i.void_reason,
            p.paid_amount_value, p.paid_amount_currency, p.paid_at,
            p.payment_method, p.transaction_reference
            """;

    /**
     * 入金は別表にある（[ADR-028] 決定 2）。
     *
     * <p><strong>1 通に 1 件だけを読む。</strong>分割入金は本 IT では扱わない
     * ——扱う段になったら、ここが「複数行を運ぶ」形に変わる。
     */
    String FROM_INVOICE = """
             FROM invoice i
             LEFT JOIN payment p
               ON p.invoice_id = i.id
            """;

    /**
     * 精算書を書く。
     *
     * <p><strong>更新は用意しない</strong>（[ADR-027] 決定 4）。発行した精算書の金額は
     * 動かない。訂正は US23（IT12）で「取り消して出し直す」形にする。
     */
    @Insert("""
            INSERT INTO invoice (
                invoice_number, booking_id, shipper_id, shipper_name, shipper_corporate, simulated,
                leg_count, leg_factor, leg_region, weight_kg, cargo_type,
                base_amount_value, base_amount_currency,
                discount_rate, discount_amount_value, discount_amount_currency,
                cancellation_fee_value, cancellation_fee_currency, cancellation_fee_rate,
                booking_status_at_cancel,
                tax_rate, tax_amount, tax_exempt, total_amount_value, total_amount_currency,
                payment_status, issued_at, due_date)
            VALUES (
                #{invoiceNumber}, #{bookingId}, #{shipperId}, #{shipperName},
                #{shipperCorporate}, #{simulated}, #{legCount}, #{legFactor}, #{legRegion},
                #{weightKg}, #{cargoType},
                #{baseAmountValue}, #{baseAmountCurrency},
                #{discountRate}, #{discountAmountValue}, #{discountAmountCurrency},
                #{cancellationFeeValue}, #{cancellationFeeCurrency}, #{cancellationFeeRate},
                #{bookingStatusAtCancel},
                #{taxRate}, #{taxAmount}, #{taxExempt},
                #{totalAmountValue}, #{totalAmountCurrency},
                #{paymentStatus}, #{issuedAt}, #{dueDate})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(InvoiceRecord row);

    @Select("SELECT " + COLUMNS + FROM_INVOICE + " WHERE i.invoice_number = #{invoiceNumber}")
    @Results(id = "invoice", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "invoice_number", property = "invoiceNumber"),
            @Result(column = "booking_id", property = "bookingId"),
            @Result(column = "shipper_id", property = "shipperId"),
            @Result(column = "simulated", property = "simulated"),
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
            @Result(column = "due_date", property = "dueDate"),
            @Result(column = "voided_at", property = "voidedAt"),
            @Result(column = "void_reason", property = "voidReason"),
            @Result(column = "paid_amount_value", property = "paidAmountValue"),
            @Result(column = "paid_amount_currency", property = "paidAmountCurrency"),
            @Result(column = "paid_at", property = "paidAt"),
            @Result(column = "payment_method", property = "paymentMethod"),
            @Result(column = "transaction_reference", property = "transactionReference"),
    })
    InvoiceRecord selectByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);

    /** **新しい順に並べる。** 発行済みの一覧は「最近出したもの」から見る。 */
    /**
     * 発行済みの一覧。
     *
     * <p><strong>シミュレーション由来は出さない</strong>（[ADR-030] 決定 3）。
     * 混ざると、支払期限超過の一覧に架空の未入金が積み上がる——督促の判断は
     * そこで行われるため実害がある。<strong>名指しの照会では外さない</strong>。
     */
    @Select("SELECT " + COLUMNS + FROM_INVOICE + " WHERE i.simulated = FALSE"
            + " ORDER BY i.issued_at DESC, i.id DESC")
    @ResultMap("invoice")
    List<InvoiceRecord> selectAll();

    /**
     * その予約に<strong>有効な</strong>精算書があるか（決定 4——二重請求を防ぐ）。
     *
     * <p><strong>取り消し済みは数えない</strong>（[ADR-028] 決定 3）。数えると、
     * 制約を出し直せる形に変えても<strong>アプリ側が先に弾く</strong>——
     * 間違えた請求書を取り消したあと、その予約に二度と請求できない。
     */
    @Select("SELECT COUNT(*) FROM invoice WHERE booking_id = #{bookingId}"
            + " AND voided_at IS NULL")
    int countByBookingId(@Param("bookingId") String bookingId);

    /**
     * 入金を記録する（受入基準 23-3）。
     *
     * <p><strong>請求書の行は書き換えない</strong>——状態だけを {@link #updateStatus} で動かす。
     */
    @Insert("""
            INSERT INTO payment (invoice_id, paid_amount_value, paid_amount_currency,
                paid_at, payment_method, transaction_reference)
            VALUES (#{invoiceId}, #{paidAmountValue}, #{paidAmountCurrency},
                #{paidAt}, #{paymentMethod}, #{transactionReference})
            """)
    void insertPayment(PaymentRecord row);

    /** 支払いの状態を動かす。**金額の列には触れない**（[ADR-027] 決定 4）。 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE invoice SET payment_status = #{paymentStatus}, updated_at = NOW()"
                    + " WHERE invoice_number = #{invoiceNumber}")
    int updateStatus(@Param("invoiceNumber") String invoiceNumber,
            @Param("paymentStatus") String paymentStatus);

    /**
     * 取り消しを記録する（赤伝・決定 3）。
     *
     * <p><strong>印も一緒に入れる。</strong>{@code void_marker} は
     * {@code (booking_id, void_marker)} の UNIQUE に効いており、これが入って初めて
     * 同じ予約に出し直せる。
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE invoice SET voided_at = #{voidedAt}, void_reason = #{voidReason},"
                    + " void_marker = invoice_number, updated_at = NOW()"
                    + " WHERE invoice_number = #{invoiceNumber} AND voided_at IS NULL")
    int updateVoided(@Param("invoiceNumber") String invoiceNumber,
            @Param("voidedAt") java.time.Instant voidedAt,
            @Param("voidReason") String voidReason);

    /** 予約 ID の請求書 id を引く（入金の記録に使う）。 */
    @Select("SELECT id FROM invoice WHERE invoice_number = #{invoiceNumber}")
    Long selectIdByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);

    /**
     * 請求番号を採番する（[ADR-011] と同じ形）。
     *
     * <p><strong>DB のシーケンスに任せる。</strong>MAX+1 の自前採番は、同時に 2 件発行
     * されたときに衝突する。
     */
    @Select("SELECT NEXTVAL('invoice_number_seq')")
    long nextInvoiceNumber();
}
