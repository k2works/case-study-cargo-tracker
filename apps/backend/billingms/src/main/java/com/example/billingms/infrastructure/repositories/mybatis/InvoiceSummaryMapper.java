package com.example.billingms.infrastructure.repositories.mybatis;

import com.example.billingms.domain.projections.InvoiceSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 請求書 Read Model 用の MyBatis Mapper（US21 / US23 / IT7 タスク 2.5）。
 *
 * <p>SQL は {@code resources/mapper/InvoiceSummaryMapper.xml} で定義する。</p>
 */
@Mapper
public interface InvoiceSummaryMapper {

    /**
     * InvoiceCalculatedEvent で呼び出される初期挿入。PENDING → CALCULATED 遷移済みの状態
     * （basic_amount + total_amount = basic_amount、discount/adjustment = 0）で行を作成する。
     */
    void insertInvoice(@Param("invoiceId") String invoiceId,
                       @Param("bookingId") String bookingId,
                       @Param("shipperId") String shipperId,
                       @Param("basicAmount") BigDecimal basicAmount,
                       @Param("currency") String currency,
                       @Param("billingStatus") String billingStatus);

    /**
     * 割引適用後の invoice 行更新（US22 / DiscountAppliedEvent 受信時）。
     * discount_amount と total_amount を更新し、updated_at + version を進める。
     */
    void updateDiscount(@Param("invoiceId") String invoiceId,
                        @Param("discountAmount") BigDecimal discountAmount,
                        @Param("totalAmount") BigDecimal totalAmount);

    InvoiceSummary findByInvoiceId(@Param("invoiceId") String invoiceId);

    InvoiceSummary findByBookingId(@Param("bookingId") String bookingId);

    /** 請求一覧用のページング取得（S22 / IT7 US23、updated_at DESC）。 */
    List<InvoiceSummary> findAll(@Param("offset") int offset, @Param("limit") int limit);

    /** 総件数（ページネーション用）。 */
    long count();

    /**
     * 精算書発行時の invoice 更新（US23 / T4.3、InvoiceIssuedEvent 受信時）。
     * invoice_number / payment_due / billing_status を更新し、updated_at + version を進める。
     */
    void updateForIssued(@Param("invoiceId") String invoiceId,
                         @Param("invoiceNumber") String invoiceNumber,
                         @Param("paymentDue") LocalDate paymentDue);

    /**
     * 入金記録時の invoice 更新（US23 / T4.3、PaymentRecordedEvent 受信時）。
     * paid_at / billing_status を更新し、updated_at + version を進める。
     */
    void updateForPaid(@Param("invoiceId") String invoiceId,
                       @Param("paidAt") LocalDateTime paidAt);

    /**
     * 督促時の invoice 更新（US23 / T4.3、InvoiceOverdueEvent 受信時）。
     * billing_status のみ OVERDUE に更新し、updated_at + version を進める。
     */
    void updateForOverdue(@Param("invoiceId") String invoiceId);

    /**
     * 部分入金時の invoice 更新（IT9 A1.5 / US26、PartialPaymentRecordedEvent 受信時）。
     * paid_so_far を加算し、billing_status を PARTIALLY_PAID に更新する。
     */
    void updateForPartiallyPaid(@Param("invoiceId") String invoiceId,
                                @Param("paidAmount") BigDecimal paidAmount);

    /**
     * 督促対象（INVOICED かつ payment_due 超過）の一覧を返す（US23 / T4.3, T4.6）。
     * OverdueScheduler および S25 督促一覧で利用。
     */
    List<InvoiceSummary> findOverdueCandidates(@Param("now") LocalDate now);

    /**
     * billing_status フィルタ付き一覧（US23 / T4.3 / S22 フィルタ）。
     */
    List<InvoiceSummary> findByStatus(@Param("billingStatus") String billingStatus,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    long countByStatus(@Param("billingStatus") String billingStatus);

    /**
     * 当日採番済の invoice_number の最大シーケンス番号を取得（US23 / T4.2）。
     *
     * <p>INV-YYYYMMDD-XXXX の末尾 4 桁を返す。当日採番なしの場合は {@code null}。
     * InvoiceNumberGenerator がインクリメントして新規番号を採番する。</p>
     *
     * @param yyyymmdd 採番対象日（YYYYMMDD 8 桁文字列）
     * @return 最大シーケンス番号（1〜9999）または {@code null}（当日採番なし）
     */
    Integer findMaxInvoiceNumberSequenceForDate(@Param("yyyymmdd") String yyyymmdd);
}
