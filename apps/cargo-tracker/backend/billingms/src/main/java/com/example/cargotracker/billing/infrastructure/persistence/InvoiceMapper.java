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
            + "issued_on, due_on, paid_at, quoted_amount, "
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
     * 発行を写す（US23 §受入基準 1）。
     *
     * <p><b>状態と期限を一緒に書く。</b> 別々に書くと、片方だけ入った行が
     * 「請求済だが期限が無い」あるいは「期限はあるが算出済」になる。</p>
     */
    int markIssued(@Param("invoiceId") String invoiceId,
            @Param("issuedOn") java.time.LocalDate issuedOn,
            @Param("dueOn") java.time.LocalDate dueOn,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("projectedAt") Instant projectedAt,
            @Param("lastEventId") String lastEventId);

    /** 入金を写す（US23 §受入基準 4）。 */
    int markPaid(@Param("invoiceId") String invoiceId,
            @Param("paidAt") Instant paidAt,
            @Param("projectedAt") Instant projectedAt,
            @Param("lastEventId") String lastEventId);

    /**
     * 取消を写す（UC18）。
     *
     * <p><b>行は消さない。</b> 消すと、取り消した事実そのものが残らない。
     * {@code void_marker} には請求書 ID を入れて、部分ユニーク
     * {@code (booking_id, void_marker)} から外す——同じ予約に新しい請求書を
     * 発行できるようにするため（不変条件 6）。</p>
     */
    int markVoided(@Param("invoiceId") String invoiceId,
            @Param("projectedAt") Instant projectedAt,
            @Param("lastEventId") String lastEventId);

    /** 入金の記録（追記専用。{@code payment_id} が PK）。 */
    int insertPayment(PaymentRow row);

    /** 発行の通知（追記専用。{@code invoice_id} が PK）。 */
    int insertNotification(NotificationRow row);

    /** その荷主に送った通知（S62 が読む）。 */
    @Select("SELECT invoice_id, shipper_id, kind, notified_at FROM invoice_notification "
            + "WHERE invoice_id = #{invoiceId}")
    NotificationRow findNotification(@Param("invoiceId") String invoiceId);

    /**
     * 未払いの請求書（US23 §受入基準 5）。
     *
     * <p><b>期限超過は列に持たない</b>（不変条件 4）。状態と期限で絞る——
     * <b>期限当日は超過ではない</b>ので {@code due_on < today} である
     * （{@code <=} にすると当日に督促が飛ぶ）。</p>
     *
     * <p>{@code today} は<b>業務タイムゾーン</b>で決めて渡す。DB の
     * {@code CURRENT_DATE} を使うと、サーバのタイムゾーンで判断される。</p>
     */
    @Select("SELECT " + COLUMNS + " FROM invoice "
            + "WHERE billing_status = 'INVOICED' AND due_on < #{today} "
            + "ORDER BY due_on, invoice_id")
    List<InvoiceRow> findOverdue(@Param("today") java.time.LocalDate today);

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

    /**
     * 明細を 1 行足す。
     *
     * <p><b>元イベントの識別子で一意にする</b>（{@code source_event_id}）。
     * 調整は算出と違って入れ直せない（別のイベントで積む）ので、同じイベントが
     * 2 度届くと {@code MAX(line_seq)+1} が新しい番号を採って同じ内容の行が
     * もう 1 行できる（IT13 T7 で実測）。</p>
     */
    int insertLineItem(LineItemRow row);

    /** 明細は入れ直す（追記専用の行はリプレイで増える。IT6 の教訓）。 */
    @Delete("DELETE FROM invoice_line_item WHERE invoice_id = #{invoiceId} "
            + "AND item_type <> 'ADJUSTMENT'")
    int deleteCalculatedLineItems(@Param("invoiceId") String invoiceId);

    @Select("SELECT invoice_id, line_seq, item_type, description, amount, currency, "
            + "basis_exception_id, source_event_id, adjustment_id, reversed_adjustment_id "
            + "FROM invoice_line_item "
            + "WHERE invoice_id = #{invoiceId} ORDER BY line_seq")
    List<LineItemRow> findLineItems(@Param("invoiceId") String invoiceId);

    /** 次の明細の並び順（調整は積み上がるので、いまある行の次に置く）。 */
    @Select("SELECT COALESCE(MAX(line_seq), 0) + 1 FROM invoice_line_item "
            + "WHERE invoice_id = #{invoiceId}")
    int nextLineSeq(@Param("invoiceId") String invoiceId);

    record PaymentRow(
            String paymentId,
            String invoiceId,
            BigDecimal amount,
            String currency,
            Instant paidAt,
            String recordedBy) {
    }

    record NotificationRow(
            String invoiceId,
            String shipperId,
            String kind,
            Instant notifiedAt) {
    }

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
            java.time.LocalDate issuedOn,
            java.time.LocalDate dueOn,
            Instant paidAt,
            BigDecimal quotedAmount,
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
            String basisExceptionId,
            String sourceEventId,
            // 調整の識別子（IT14 引き継ぎ C）。取り消すときの宛先。
            String adjustmentId,
            // どの調整の取り消しか。入っていれば、この行は取り消しである。
            String reversedAdjustmentId) {
    }
}
