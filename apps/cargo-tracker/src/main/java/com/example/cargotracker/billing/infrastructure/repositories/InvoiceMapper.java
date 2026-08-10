package com.example.cargotracker.billing.infrastructure.repositories;

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
            SELECT id, invoice_number AS invoiceNumber, booking_id AS bookingId,
                   shipper_id AS shipperId,
                   base_amount_value AS baseAmountValue,
                   base_amount_currency AS baseAmountCurrency,
                   discount_rate AS discountRate,
                   discount_amount_value AS discountAmountValue,
                   discount_amount_currency AS discountAmountCurrency,
                   tax_rate AS taxRate,
                   tax_amount_value AS taxAmountValue,
                   tax_amount_currency AS taxAmountCurrency,
                   total_amount_value AS totalAmountValue,
                   total_amount_currency AS totalAmountCurrency,
                   charge_status AS chargeStatus,
                   adjustment_reduction_value AS adjustmentReductionValue,
                   adjustment_compensation_value AS adjustmentCompensationValue,
                   adjustment_currency AS adjustmentCurrency,
                   adjustment_reason AS adjustmentReason,
                   version
              FROM invoice
             WHERE invoice_number = #{invoiceNumber}
            """)
    InvoiceRecord findByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);

    @Select("""
            SELECT id, invoice_number AS invoiceNumber, booking_id AS bookingId,
                   shipper_id AS shipperId,
                   base_amount_value AS baseAmountValue,
                   base_amount_currency AS baseAmountCurrency,
                   discount_rate AS discountRate,
                   discount_amount_value AS discountAmountValue,
                   discount_amount_currency AS discountAmountCurrency,
                   tax_rate AS taxRate,
                   tax_amount_value AS taxAmountValue,
                   tax_amount_currency AS taxAmountCurrency,
                   total_amount_value AS totalAmountValue,
                   total_amount_currency AS totalAmountCurrency,
                   charge_status AS chargeStatus,
                   adjustment_reduction_value AS adjustmentReductionValue,
                   adjustment_compensation_value AS adjustmentCompensationValue,
                   adjustment_currency AS adjustmentCurrency,
                   adjustment_reason AS adjustmentReason,
                   version
              FROM invoice
             WHERE booking_id = #{bookingId}
            """)
    InvoiceRecord findByBookingId(@Param("bookingId") UUID bookingId);

    /** 精算書番号の採番（連番）。 */
    @Select("SELECT nextval('invoice_number_seq')")
    long nextSequence();
}
