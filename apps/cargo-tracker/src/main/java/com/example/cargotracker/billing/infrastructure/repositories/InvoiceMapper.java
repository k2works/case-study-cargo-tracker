package com.example.cargotracker.billing.infrastructure.repositories;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 精算書の読み書き（US21 / US22）。
 *
 * <p><strong>触るのは Billing のテーブルだけである</strong>（ADR-015。
 * {@code MapperTableOwnershipTest} が検査する）。荷主名や貨物の情報が要る場合は
 * ACL ポートで受け取る。
 */
@Mapper
public interface InvoiceMapper {

    /**
     * 精算書 1 行の読み出し（<strong>入金の記録を含む</strong>）。
     *
     * <p><strong>同じ列並びを 4 か所に書き写さない。</strong> 列を足すたびに
     * 書き足し忘れた 1 か所だけが古くなり、<strong>その経路でだけ値が消える</strong>。
     *
     * <p><strong>入金は LEFT JOIN で一緒に読む</strong>（IT13 レビュー C4 の型）。
     * 1 件ずつ引き直すと、一覧の行数に比例して問い合わせが増える。
     * {@code payment} は Billing 自身のテーブルであり、BC はまたがない（ADR-015）。
     */
    String SELECT_INVOICE = """
            SELECT i.id, i.invoice_number AS invoiceNumber, i.booking_id AS bookingId,
                   i.shipper_id AS shipperId,
                   i.shipper_name AS shipperName, i.tracking_number AS trackingNumber,
                   i.corporate,
                   i.base_amount_value AS baseAmountValue,
                   i.base_amount_currency AS baseAmountCurrency,
                   i.discount_rate AS discountRate,
                   i.discount_amount_value AS discountAmountValue,
                   i.discount_amount_currency AS discountAmountCurrency,
                   i.tax_rate AS taxRate,
                   i.tax_amount_value AS taxAmountValue,
                   i.tax_amount_currency AS taxAmountCurrency,
                   i.total_amount_value AS totalAmountValue,
                   i.total_amount_currency AS totalAmountCurrency,
                   i.charge_status AS chargeStatus,
                   i.payment_status AS paymentStatus,
                   i.issued_at AS issuedAt,
                   i.due_date AS dueDate,
                   i.adjustment_reduction_value AS adjustmentReductionValue,
                   i.adjustment_compensation_value AS adjustmentCompensationValue,
                   i.adjustment_currency AS adjustmentCurrency,
                   i.adjustment_reason AS adjustmentReason,
                   i.version,
                   p.paid_amount_value AS paidAmountValue,
                   p.paid_amount_currency AS paidAmountCurrency,
                   p.paid_at AS paidAt,
                   p.payment_method AS paymentMethod,
                   p.transaction_reference AS transactionReference
              FROM invoice i
              LEFT JOIN payment p ON p.invoice_id = i.id
            """;

    /**
     * 新しい精算書を書く。
     *
     * <p><strong>支払い状態は PENDING で始める。</strong> 料金の状態
     * （{@code charge_status}）とは別の軸である（ADR-017）。
     * 精算書の発行と入金の確認は US23（IT14）の領分であり、
     * <strong>ここでは触らない</strong>。
     */
    @Insert("""
            INSERT INTO invoice (
                invoice_number, booking_id, shipper_id,
                shipper_name, tracking_number, corporate,
                base_amount_value, base_amount_currency,
                discount_rate, discount_amount_value, discount_amount_currency,
                tax_rate, tax_amount_value, tax_amount_currency,
                total_amount_value, total_amount_currency,
                charge_status, payment_status,
                adjustment_reduction_value, adjustment_compensation_value,
                adjustment_currency, adjustment_reason,
                version)
            VALUES (
                #{invoiceNumber}, #{bookingId}, #{shipperId},
                #{shipperName}, #{trackingNumber}, #{corporate},
                #{baseAmountValue}, #{baseAmountCurrency},
                #{discountRate}, #{discountAmountValue}, #{discountAmountCurrency},
                #{taxRate}, #{taxAmountValue}, #{taxAmountCurrency},
                #{totalAmountValue}, #{totalAmountCurrency},
                #{chargeStatus}, 'PENDING',
                #{adjustmentReductionValue}, #{adjustmentCompensationValue},
                #{adjustmentCurrency}, #{adjustmentReason},
                0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InvoiceRecord row);

    /**
     * 更新する（楽観的ロック）。
     *
     * <p><strong>確定済みの精算書は更新できない。</strong> WHERE に
     * {@code charge_status = 'DRAFT'} を置く — <strong>ドメインの守りと同じ条件を
     * SQL にも書く</strong>。集約を通らない経路が生まれても金額が動かない。
     */
    @Update("""
            UPDATE invoice
               SET discount_rate         = #{discountRate},
                   discount_amount_value = #{discountAmountValue},
                   discount_amount_currency = #{discountAmountCurrency},
                   tax_amount_value      = #{taxAmountValue},
                   total_amount_value    = #{totalAmountValue},
                   charge_status         = #{chargeStatus},
                   adjustment_reduction_value    = #{adjustmentReductionValue},
                   adjustment_compensation_value = #{adjustmentCompensationValue},
                   adjustment_currency           = #{adjustmentCurrency},
                   adjustment_reason             = #{adjustmentReason},
                   version    = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE invoice_number = #{invoiceNumber}
               AND version        = #{version}
               AND charge_status  = 'DRAFT'
            """)
    int update(InvoiceRecord row);

    @Select("""
            """ + SELECT_INVOICE + """
             WHERE i.invoice_number = #{invoiceNumber}
            """)
    InvoiceRecord findByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);

    @Select("""
            """ + SELECT_INVOICE + """
             WHERE i.booking_id = #{bookingId}
            """)
    InvoiceRecord findByBookingId(@Param("bookingId") UUID bookingId);

    /** 精算書番号の採番（連番）。 */
    @Select("SELECT nextval('invoice_number_seq')")
    long nextSequence();

    /**
     * 全件（請求書一覧）。
     *
     * <p><strong>行をまるごと返す</strong>（IT13 レビュー C4）。番号だけを取って
     * 1 件ずつ引き直すと、行数に比例して問い合わせが増える。
     *
     * <p><strong>絞り込みと分けている。</strong> 1 つのクエリで
     * {@code #{status} IS NULL} と書くと、PostgreSQL が
     * <strong>「パラメータの型を決められない」で落ちる</strong>
     * （IT13 では絞り込みなしの経路を踏むテストが無く、気づかなかった）。
     */
    @Select("""
            """ + SELECT_INVOICE + """
             ORDER BY i.id DESC
            """)
    List<InvoiceRecord> findAll();

    /** 料金の状態で絞る（請求書一覧）。<strong>行をまるごと返す</strong>（C4）。 */
    @Select("""
            """ + SELECT_INVOICE + """
             WHERE i.charge_status = #{chargeStatus}
             ORDER BY i.id DESC
            """)
    List<InvoiceRecord> findByChargeStatus(@Param("chargeStatus") String chargeStatus);

    /** 支払いの状態で絞る（督促対象一覧）。<strong>行をまるごと返す</strong>（C4）。 */
    @Select("""
            """ + SELECT_INVOICE + """
             WHERE i.payment_status = #{paymentStatus}
               AND i.issued_at IS NOT NULL
             ORDER BY i.due_date, i.id
            """)
    List<InvoiceRecord> findByPaymentStatus(@Param("paymentStatus") String paymentStatus);

    /**
     * 発行・入金・期限超過を反映する（US23）。楽観的ロック付き。
     *
     * <p><strong>金額の更新と分ける。</strong> {@code update} は
     * {@code charge_status = 'DRAFT'} を条件に持つ（確定後に金額を動かさないため）。
     * 精算は<strong>確定済みにだけ起きる</strong>ので、同じ SQL では書けない。
     *
     * <p><strong>WHERE に確定済みを書く</strong> — ドメインの守りと同じ条件を
     * SQL にも置く。集約を通らない経路が生まれても、下書きは発行されない。
     */
    @Update("""
            UPDATE invoice
               SET issued_at      = #{issuedAt},
                   due_date       = #{dueDate},
                   payment_status = #{paymentStatus},
                   version        = version + 1,
                   updated_at     = CURRENT_TIMESTAMP
             WHERE invoice_number = #{invoiceNumber}
               AND version        = #{version}
               AND charge_status  = 'CONFIRMED'
            """)
    int updateSettlement(InvoiceRecord row);

    /**
     * 入金を記録する（US23）。
     *
     * <p><strong>1 請求書 1 行として使う。</strong> 複数行を許す構造だが、
     * 一部入金を認めないため 1 行しか作らない。
     * <strong>構造が許すことと、業務が許すことは別である。</strong>
     */
    @Insert("""
            INSERT INTO payment (
                invoice_id, paid_amount_value, paid_amount_currency,
                paid_at, payment_method, transaction_reference)
            VALUES (
                #{invoiceId}, #{paidAmountValue}, #{paidAmountCurrency},
                #{paidAt}, #{paymentMethod}, #{transactionReference})
            """)
    int insertPayment(PaymentRecord row);

    /**
     * 請求済みの予約 ID（請求対象一覧の絞り込み）。
     *
     * <p><strong>1 件ずつ「請求書があるか」を聞かない</strong>（C4）。
     * まとめて引いて、呼び出し側が集合として使う。
     */
    @Select("SELECT booking_id FROM invoice")
    List<UUID> findInvoicedBookingIds();
}
