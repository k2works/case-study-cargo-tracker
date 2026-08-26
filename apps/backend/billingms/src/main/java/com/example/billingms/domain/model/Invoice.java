package com.example.billingms.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 精算書（US21・[ADR-027] 決定 3・決定 4）。
 *
 * <p><strong>算出中の精算書は存在しない</strong>（決定 3）。経理担当者が確定操作をした時点で
 * 初めて発行される。下書きを持つと、下書きのまま忘れられた精算書が溜まる——それを見つける
 * 手段をまた作ることになる。
 *
 * <p><strong>発行した精算書の金額は動かない</strong>（決定 4）。請求書は荷主へ出す約束であり、
 * 出したあとに黙って変わると請求の根拠が消える。金額を変える手段をここに置かない。
 * 訂正は US23（IT12）で「取り消して出し直す」形にする。
 *
 * <p><strong>根拠を持ったまま発行する。</strong>基本料金の内訳（区間数・重量・貨物種別）、
 * 割引率、キャンセル料の料率と申請時の状態——金額だけを持つと、あとから
 * 「なぜその金額か」を答えられない。経理担当者は請求の根拠を荷主に説明する。
 */
public final class Invoice {

    /** 宛名（誰に・どの予約に・いつ出したか）。**発行時に確定し、以後は変わらない。** */
    private final InvoiceHeader header;
    private final InvoiceCharges charges;
    private final InvoiceAmounts amounts;
    private final List<InvoiceLineItem> lineItems;
    /** 発行したあとに起きたこと（支払い・取り消し）。**金額は入らない**（決定 4）。 */
    private final InvoiceLifecycle lifecycle;

    /** 支払期限は発行日から何日後か（正典のビジネスルール 2）。 */
    private static final int PAYMENT_TERM_DAYS = 30;

    private Invoice(InvoiceHeader header, InvoiceCharges charges, InvoiceAmounts amounts,
            List<InvoiceLineItem> lineItems, InvoiceLifecycle lifecycle) {
        this.header = header;
        this.charges = charges;
        this.amounts = amounts;
        // **写して持つ。** 呼び出し元が渡したあとの書き換えでこちらの中身が変わらないように
        this.lineItems = List.copyOf(lineItems);
        this.lifecycle = lifecycle;
    }

    /**
     * 精算書を発行する（US21-4・US21-5）。
     *
     * <p><strong>発行の時点では未入金である</strong>（決定 3）。入金の確認は US23。
     */
    public static Invoice issue(InvoiceHeader header, InvoiceCharges charges,
            List<InvoiceLineItem> lineItems, ZoneId businessZone) {
        if (header == null) {
            throw new IllegalArgumentException("請求書の宛名を指定してください");
        }
        header.requireComplete();
        if (charges == null) {
            throw new IllegalArgumentException("金額の材料を指定してください");
        }
        List<InvoiceLineItem> items = lineItems == null ? List.of() : lineItems;
        // **発行の時点で金額を確定させる**（決定 4）。以後は係数から計算し直さない
        return new Invoice(header, charges, InvoiceAmounts.calculate(charges, items), items,
                InvoiceLifecycle.issued(dueDateOf(header.issuedAt(), businessZone)));
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>ここでは検査しない</strong>（新しい不変条件は既存行を壊す）。
     * 検査するのは新規に受け入れるとき（{@link #issue}）である。
     */
    public static Invoice restore(InvoiceHeader header, InvoiceCharges charges,
            InvoiceAmounts amounts, List<InvoiceLineItem> lineItems,
            InvoiceLifecycle lifecycle) {
        // **保存された金額をそのまま受け取る**（決定 4）。係数から計算し直すと、
        // 基準運賃や税率を将来変えた瞬間に過去の請求書の金額が変わる
        return new Invoice(header, charges, amounts,
                lineItems == null ? List.of() : lineItems, lifecycle);
    }

    /**
     * 支払期限（受入基準 23-1）。**発行日から 30 日後**。
     *
     * <p><strong>業務の暦で決める。</strong>UTC で決めると、時差の分だけ期限が
     * 1 日ずれる日が出る——日中しか動かしていないと気づかない。
     */
    private static LocalDate dueDateOf(Instant issuedAt, ZoneId businessZone) {
        if (businessZone == null) {
            throw new IllegalArgumentException("業務タイムゾーンを指定してください");
        }
        return LocalDate.ofInstant(issuedAt, businessZone).plusDays(PAYMENT_TERM_DAYS);
    }

    /**
     * 入金を確認する（受入基準 23-4）。
     *
     * <p><strong>金額は動かない</strong>（[ADR-027] 決定 4）。入金は請求書に起きた
     * 別の出来事であり、請求額を変えるものではない。
     *
     * <p><strong>二度は確認しない。</strong>2 回目を通すと、入金が 2 件あったのか
     * 操作を重ねただけなのかが、あとから区別できない。
     */
    public Invoice confirmPayment(Payment confirmed) {
        if (confirmed == null) {
            throw new IllegalArgumentException("入金の記録を指定してください");
        }
        if (voided()) {
            throw new IllegalStateException(
                    "取り消した請求書に入金は確認できません: " + invoiceId().value());
        }
        if (lifecycle.isPaid()) {
            throw new IllegalStateException(
                    "すでに入金を確認しています: " + invoiceId().value());
        }
        return new Invoice(header, charges, amounts, lineItems,
                lifecycle.withPayment(confirmed));
    }

    /**
     * 取り消す（赤伝・[ADR-028] 決定 3）。
     *
     * <p><strong>消さずに取り消す。</strong>経理担当者は「DB を直すのは監査に耐えない」と
     * 明言した。行は残し、取り消したことと理由を足す。出し直しは新しい請求番号で行う。
     *
     * <p><strong>支払いの状態には混ぜない</strong>（決定 4）。取り消しは請求書そのものの
     * 状態であり、支払いの状態ではない。
     */
    public Invoice revoke(String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "取り消しの理由を入力してください（あとから二重発行の失敗と区別できません）");
        }
        if (at == null) {
            throw new IllegalArgumentException("取り消しの日時を指定してください");
        }
        if (voided()) {
            throw new IllegalStateException("すでに取り消しています: " + invoiceId().value());
        }
        if (lifecycle.isPaid()) {
            throw new IllegalStateException(
                    "入金済の請求書は取り消せません（返金は別の手続きです）: " + invoiceId().value());
        }
        return new Invoice(header, charges, amounts, lineItems, lifecycle.voided(at, reason));
    }

    /**
     * 支払期限を過ぎているか（[ADR-028] 決定 5）。
     *
     * <p><strong>列に書いて溜めない。</strong>書き込む相手（バッチ）が無いため、
     * 書いた列は誰にも更新されず、期限を過ぎた請求が一覧に現れない。
     *
     * <p><strong>日付単位で比べる。</strong>期限当日は超過ではない——時刻付きで比べると、
     * 当日に払った荷主が「遅れた」ことになる。
     */
    public boolean overdue(LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("基準の日付を指定してください");
        }
        if (voided() || lifecycle.isPaid() || lifecycle.dueDate() == null) {
            return false;
        }
        return today.isAfter(lifecycle.dueDate());
    }

    /** 発行したあとに起きたこと（支払い・取り消し）。 */
    public InvoiceLifecycle lifecycle() {
        return lifecycle;
    }

    /** 支払期限（受入基準 23-1）。 */
    public LocalDate dueDate() {
        return lifecycle.dueDate();
    }

    /** 入金の記録。**未入金なら {@code null}**。 */
    public Payment payment() {
        return lifecycle.payment();
    }

    /** 取り消したか（赤伝）。 */
    public boolean voided() {
        return lifecycle.isVoided();
    }

    public Instant voidedAt() {
        return lifecycle.voidedAt();
    }

    /** 取り消した理由。**取り消していなければ {@code null}**。 */
    public String voidReason() {
        return lifecycle.voidReason();
    }

    /** 宛名（誰に・どの予約に・いつ出したか）。 */
    public InvoiceHeader header() {
        return header;
    }

    public InvoiceId invoiceId() {
        return header.invoiceId();
    }

    public BillingBookingId cargoBookingId() {
        return header.cargoBookingId();
    }

    public BillingShipperId shipperId() {
        return header.shipperId();
    }

    /**
     * 発行した時点の荷主の社名。
     *
     * <p><strong>荷主 ID から毎回引き直さない。</strong>社名を変えた途端に発行済みの
     * 請求書の宛名まで変わるのは、出した書面が後から書き換わるのと同じである
     * （決定 4 が禁じていること）。
     */
    public String shipperName() {
        return header.shipperId().name();
    }

    /** 金額の材料（決定 1・決定 6・決定 8）。 */
    public InvoiceCharges charges() {
        return charges;
    }

    /** 基本料金の根拠（決定 1）。 */
    public TransportCharge charge() {
        return charges.charge();
    }

    public Money baseAmount() {
        return amounts.baseAmount();
    }

    /** 割引率。**割引が無ければ {@code null}**——0% と契約なしを区別する（[ADR-012]）。 */
    public DiscountRate discountRate() {
        return charges.discountRate();
    }

    public Money discountAmount() {
        return amounts.discountAmount();
    }

    /** キャンセル料。キャンセルされていなければ {@code null}。 */
    public CancellationFee cancellationFee() {
        return charges.cancellationFee();
    }

    /** 調整の明細。**発行後は足せない**（決定 4）。 */
    public List<InvoiceLineItem> lineItems() {
        return lineItems;
    }

    public TaxRate taxRate() {
        return charges.taxRate();
    }

    /** 税を含まない小計。**発行時に確定した値から導く**（計算し直さない）。 */
    public Money subtotal() {
        return amounts.subtotal();
    }

    public Money taxAmount() {
        return amounts.taxAmount();
    }

    public Money totalAmount() {
        return amounts.totalAmount();
    }

    /** 発行した時点で確定した金額（決定 4）。 */
    public InvoiceAmounts amounts() {
        return amounts;
    }

    public PaymentStatus paymentStatus() {
        return lifecycle.paymentStatus();
    }

    public Instant issuedAt() {
        return header.issuedAt();
    }
}
