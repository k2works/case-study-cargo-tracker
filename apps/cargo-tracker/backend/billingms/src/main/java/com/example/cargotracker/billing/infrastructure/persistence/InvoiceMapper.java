package com.example.cargotracker.billing.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 請求書の読み取りモデル（data-model.md「billing_read_db」）。 */
@Mapper
public interface InvoiceMapper {

    /** 読み出す列を並べる。<b>{@code SELECT *} にしない</b>（列順で割り当てられる）。 */
    String COLUMNS = "invoice_id, booking_id, shipper_id, shipper_name, shipper_type, "
            + "contract_number, base_amount, discount_amount, adjustment_amount, tax_amount, "
            + "total_amount, currency, discount_rate, billing_status, calculated_at, "
            + "projected_at, last_event_id";

    /**
     * 算出を写す。
     *
     * <p><b>衝突先を書かない</b>（{@code ON CONFLICT DO NOTHING}）。主キーのほかに
     * 「予約ごとに 1 通」の部分ユニークがあるので、衝突先を 1 つに絞ると
     * <b>リプレイのたびに別の索引で例外になる</b>。見送った事実は投影が読み直して
     * 記録する（不変条件 2 の三段目）。</p>
     */
    int insert(InvoiceRow row);

    /** 調整を写す（金額はイベントが持つ合計で上書きする）。 */
    int updateAmounts(@Param("invoiceId") String invoiceId,
            @Param("adjustmentAmount") BigDecimal adjustmentAmount,
            @Param("taxAmount") BigDecimal taxAmount,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("projectedAt") Instant projectedAt,
            @Param("lastEventId") String lastEventId);

    @Select("SELECT " + COLUMNS + " FROM invoice WHERE invoice_id = #{invoiceId}")
    InvoiceRow find(@Param("invoiceId") String invoiceId);

    /**
     * その予約の<b>有効な</b>請求書（不変条件 2 の一段目が読む）。
     *
     * <p>取り消した請求書（{@code void_marker} が入っている）は数えない。</p>
     */
    @Select("SELECT " + COLUMNS + " FROM invoice "
            + "WHERE booking_id = #{bookingId} AND void_marker = ''")
    InvoiceRow findActiveByBooking(@Param("bookingId") String bookingId);

    /**
     * 一覧（S60）。
     *
     * <p><b>既定で入金済・取消を外す。</b> 決着したものが混ざると、一覧全体が
     * 「まだ手を入れる場所」に見えなくなる。<b>並びは算出日時の新しい順</b>——
     * 正典の一覧規約は「支払期限が近い順」だが、期限が決まるのは発行のとき
     * （US23・IT14）で、いまは入っていない列で並べても順序が決まらない。</p>
     */
    List<InvoiceRow> search(@Param("includeSettled") boolean includeSettled,
            @Param("bookingId") String bookingId,
            @Param("limit") int limit);

    int insertLineItem(LineItemRow row);

    /** 明細は入れ直す（追記専用の行はリプレイで増える。IT6 の教訓）。 */
    @Delete("DELETE FROM invoice_line_item WHERE invoice_id = #{invoiceId} "
            + "AND item_type <> 'ADJUSTMENT'")
    int deleteCalculatedLineItems(@Param("invoiceId") String invoiceId);

    @Select("SELECT invoice_id, line_seq, item_type, description, amount, currency, "
            + "basis_exception_id FROM invoice_line_item WHERE invoice_id = #{invoiceId} "
            + "ORDER BY line_seq")
    List<LineItemRow> findLineItems(@Param("invoiceId") String invoiceId);

    /** 次の明細の並び順（調整は積み上がるので、いまある行の次に置く）。 */
    @Select("SELECT COALESCE(MAX(line_seq), 0) + 1 FROM invoice_line_item "
            + "WHERE invoice_id = #{invoiceId}")
    int nextLineSeq(@Param("invoiceId") String invoiceId);

    record InvoiceRow(
            String invoiceId,
            String bookingId,
            String shipperId,
            String shipperName,
            String shipperType,
            String contractNumber,
            BigDecimal baseAmount,
            BigDecimal discountAmount,
            BigDecimal adjustmentAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String currency,
            BigDecimal discountRate,
            String billingStatus,
            Instant calculatedAt,
            Instant projectedAt,
            String lastEventId) {
    }

    record LineItemRow(
            String invoiceId,
            int lineSeq,
            String itemType,
            String description,
            BigDecimal amount,
            String currency,
            String basisExceptionId) {
    }
}
