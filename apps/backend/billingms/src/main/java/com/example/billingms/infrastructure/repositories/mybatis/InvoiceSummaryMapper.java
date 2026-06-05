package com.example.billingms.infrastructure.repositories.mybatis;

import com.example.billingms.domain.projections.InvoiceSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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
