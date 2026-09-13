package com.example.cargotracker.billing.domain.model.aggregates;

import com.example.cargotracker.billing.domain.model.commands.AdjustInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.CalculateInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.IssueInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.RecordPaymentCommand;
import com.example.cargotracker.billing.domain.model.commands.ReverseAdjustmentCommand;
import com.example.cargotracker.billing.domain.model.commands.VoidInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.VoidPaymentCommand;
import com.example.cargotracker.billing.domain.model.events.InvoiceAdjustedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceIssuedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceVoidedEvent;
import com.example.cargotracker.billing.domain.model.valueobjects.BillingStatus;
import com.example.cargotracker.billing.domain.model.valueobjects.FreightCharge;
import com.example.cargotracker.billing.domain.model.valueobjects.LineItemType;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import com.example.cargotracker.billing.domain.model.valueobjects.PaymentTerm;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTable;
import com.example.cargotracker.billing.domain.service.DiscountPolicy;
import com.example.cargotracker.billing.domain.service.FreightChargeCalculator;
import com.example.cargotracker.shared.contract.event.PaymentRecordedEvent;
import com.example.cargotracker.shared.contract.event.PaymentVoidedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 請求書 1 通（UC17 / US21・US22）。
 *
 * <p><b>算出できたときに初めて集約ができる。</b> 重量や荷主の契約が分からなければ
 * 請求書を作らず、要確認へ出す（連鎖側の仕事）。だから {@code PENDING} は
 * この版の経路では通らない。</p>
 *
 * <p><b>割引は算出の中で当てる</b>（計画の注 N10）。別コマンドにすると、割引の
 * 無い請求書が一瞬見える状態が正常系として存在する。</p>
 *
 * <p><b>「有効な請求書は予約ごとに 1 通」（不変条件 2）はここでは守りきれない。</b>
 * 1 請求書 1 集約なので、同じ予約の他の請求書を知らない。三段で守る——
 * application 層の存在確認・投影の部分ユニーク・拒否の記録。ここで守れるのは
 * 「同じ請求書に二度算出しない」までである。</p>
 *
 * <p><b>期限超過は列に持たない。</b> {@link #overdue(LocalDate)} で判定する
 * （不変条件 4）。列に持つと、日付が変わるたびに全件を書き換えることになり、
 * 書き換えそこねた行が静かに未払いから漏れる。<b>期限当日は超過ではない</b>。</p>
 */
@EventSourced(idType = String.class, tagKey = "invoiceId")
public class Invoice {

    private String invoiceId;
    private BillingStatus status;
    private Money baseAmount;
    private Money discountAmount;

    /**
     * 調整の累計（<b>符号つき</b>）。
     *
     * <p>{@link Money} は負を許さない（請求書に負の基本料金は無い）が、調整は
     * 減額もありうる。累計だけは生の値で持つ。</p>
     */
    private BigDecimal adjustmentTotal = BigDecimal.ZERO;

    /**
     * まだ取り消されていない調整（識別子 → 金額）。
     *
     * <p><b>取り消せるものを集約が知っている必要がある。</b> 投影に尋ねると、
     * 投影が追いついていないあいだは取り消せない／二重に取り消せるの両方が
     * 起こる。</p>
     *
     * <p><b>識別子の無い調整（IT13 までの記録）は入らない。</b> 列が無かった
     * ころの記録を読めなくしない——復元では検査せず、取り消そうとしたときに
     * 「取り消せません」と答える。</p>
     */
    private final Map<String, BigDecimal> reversibleAdjustments = new LinkedHashMap<>();

    /**
     * 輸出免税の請求書か。調整のたびに税を数え直すのに要る。
     *
     * <p><b>税額と合計そのものは持たない。</b> 調整のたびに数え直すので、
     * 持っても読まない——<b>読む側の無い状態を先に持たない</b>（IT12 の
     * 「常に 0 の列」と同じ形）。金額は投影が持ち、画面はそちらを読む。</p>
     */
    private boolean taxExempt;

    /** 予約と荷主。<b>発行と入金のイベントに載せる</b>（購読側の投影が作れる分を運ぶ）。 */
    private String bookingId;
    private String shipperId;

    /**
     * 支払期限。<b>発行のときに確定する</b>（不変条件 3）。
     *
     * <p>未発行なら {@code null} で、{@link #overdue(LocalDate)} は常に false を
     * 返す——発行していない請求書に「期限を過ぎた」は無い。</p>
     */
    private LocalDate dueOn;

    /**
     * 記録されていて、まだ取り消されていない入金の識別子。
     *
     * <p><b>状態だけでは足りない。</b> 「入金済だから取り消せる」で通すと、
     * <b>どの入金を取り消したのかが残らない</b>——取り違えたのが別の入金なら、
     * 誤った行に印が付く。一部入金を扱わないので高々 1 件である。</p>
     */
    private String paymentId;

    /**
     * 税率。<b>算出時のものを覚えておく</b>——あとで料率が変わっても、出した
     * 請求書は変わらない。<b>イベントから写す</b>（割り戻さない）。
     */
    private BigDecimal taxRate;

    @EntityCreator
    public Invoice() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 輸送料金を算出する（US21 §受入基準 3・5／US22）。
     *
     * <p><b>static ではなくインスタンスのハンドラにする。</b> 両方置くと、集約が
     * 既に存在しても static のほうが呼ばれ、2 度目の算出が通る（IT2 で実測）。</p>
     */
    @CommandHandler
    public String calculate(CalculateInvoiceCommand command, EventAppender appender,
            FreightChargeCalculator calculator, DiscountPolicy discountPolicy,
            RateTable rates, Clock clock) {
        if (invoiceId != null) {
            throw new IllegalTransition(
                    "請求書 " + invoiceId + " はすでに算出されています");
        }
        requireText(command.invoiceId(), "請求書 ID は必須です");
        requireText(command.bookingId(), "予約 ID は必須です");
        requireText(command.shipperId(), "荷主 ID は必須です");
        if (command.shipperType() == null) {
            throw new BusinessRuleViolation("荷主種別が分からないので割引を判断できません");
        }
        if (command.transport() == null) {
            throw new BusinessRuleViolation("輸送実績が無いので料金を算出できません");
        }

        FreightCharge charge = calculator.calculate(command.transport(), rates);
        Money base = charge.baseCharge();
        Money discount = discountPolicy.discountFor(command.shipperType(),
                command.discountRate(), base);
        Money afterDiscount = base.subtract(discount);
        Money tax = calculator.taxOn(command.transport(), afterDiscount, rates);
        Money total = afterDiscount.add(tax);

        List<InvoiceCalculatedEvent.LineItem> items = new ArrayList<>();
        items.add(item(LineItemType.BASE, "基本料金（" + charge.description() + "）", base));
        if (!discount.isZero()) {
            items.add(item(LineItemType.DISCOUNT, discountDescription(command), discount));
        }
        if (!tax.isZero()) {
            items.add(item(LineItemType.TAX, "消費税", tax));
        } else if (command.transport().isExport()) {
            items.add(item(LineItemType.TAX, "消費税（輸出免税）", Money.zero()));
        }

        appender.append(new InvoiceCalculatedEvent(command.invoiceId(), command.bookingId(),
                command.shipperId(), command.shipperName(), command.shipperType().name(),
                command.contractNumber(), command.discountRate().value(),
                round(base), round(discount), round(tax),
                // **税率と免税を載せる。** 割り戻すと、税率 0% の期間に国内貨物が
                // 免税として復元される（IT13 のレビュー 中）。
                rates.taxRate(), command.transport().isExport(),
                round(total), base.currency(),
                // **そのまま持つ。計算し直さない**（不変条件 7）。
                command.quotedAmount(),
                items, command.calculatedBy(), clock.instant()));
        return command.invoiceId();
    }

    /**
     * 料金を調整する（US21 §受入基準 6）。
     *
     * <p><b>理由は必須</b>——何が起きたか読めない記録を残さない。<b>0 円の調整も
     * 断る</b>（履歴に意味の無い行を積まない）。</p>
     *
     * <p><b>税は調整後の額で数え直す。</b> 調整は課税対象そのものを動かすので、
     * 算出時の税額を据え置くと合計が合わない。</p>
     */
    @CommandHandler
    public void adjust(AdjustInvoiceCommand command, EventAppender appender, Clock clock) {
        if (invoiceId == null) {
            throw new IllegalTransition("請求書 " + command.invoiceId() + " がありません");
        }
        if (!status.acceptsAdjustment()) {
            throw new IllegalTransition(
                    "状態 " + status.label() + " の請求書は調整できません");
        }
        requireText(command.reason(), "調整の理由は必須です");
        requireText(command.adjustedBy(), "調整した人は必須です");
        if (command.amount() == null || command.amount().signum() == 0) {
            throw new BusinessRuleViolation("調整額が 0 円です（動かない調整は記録しない）");
        }

        BigDecimal adjusted = adjustmentTotal.add(command.amount());
        Money afterDiscount = baseAmount.subtract(discountAmount);
        BigDecimal taxable = afterDiscount.amount().add(adjusted);
        if (taxable.signum() < 0) {
            // **黙って 0 にしない。** 割引と調整が基本料金を超えたなら、それは
            // 入力の誤りで、0 円の請求書として出してよいものではない。
            throw new BusinessRuleViolation(
                    "調整すると請求額が負になります: " + taxable);
        }

        Money newTaxable = Money.yen(taxable);
        Money newTax = taxExempt ? Money.zero() : newTaxable.multiply(taxRate);
        Money newTotal = newTaxable.add(newTax);

        requireText(command.adjustmentId(), "調整の識別子は必須です");
        if (reversibleAdjustments.containsKey(command.adjustmentId())) {
            throw new IllegalTransition(
                    "調整 " + command.adjustmentId() + " はすでに入っています");
        }

        appender.append(new InvoiceAdjustedEvent(invoiceId, command.adjustmentId(), null,
                command.amount(), command.reason(),
                command.basisExceptionId(), adjusted, round(newTax), round(newTotal),
                baseAmount.currency(), command.adjustedBy(), clock.instant()));
    }

    /**
     * 調整を取り消す（IT14 引き継ぎ C）。
     *
     * <p><b>消さずに反対向きを積む。</b> 何が起きたかを追えるようにしておかな
     * ければ、経理が確かめられない。取り消したぶんは合計から引かれる。</p>
     *
     * <p><b>2 度取り消せない。</b> 取り消した調整を取り消すと、入れ直したのと
     * 同じになり、誰も意図していない額になる。</p>
     */
    @CommandHandler
    public void reverseAdjustment(ReverseAdjustmentCommand command, EventAppender appender,
            Clock clock) {
        if (invoiceId == null) {
            throw new IllegalTransition("請求書 " + command.invoiceId() + " がありません");
        }
        if (!status.acceptsAdjustment()) {
            throw new IllegalTransition(
                    "状態 " + status.label() + " の請求書は調整を取り消せません");
        }
        requireText(command.reason(), "取り消しの理由は必須です");
        requireText(command.reversedBy(), "取り消した人は必須です");

        BigDecimal original = reversibleAdjustments.get(command.adjustmentId());
        if (original == null) {
            // **未登録を素通りさせない。** 取り消し済み・存在しない・識別子の
            // 無い古い調整は、どれも「いま取り消せるもの」ではない。
            throw new IllegalTransition(
                    "取り消せる調整がありません: " + command.adjustmentId());
        }

        BigDecimal adjusted = adjustmentTotal.subtract(original);
        Money afterDiscount = baseAmount.subtract(discountAmount);
        Money newTaxable = Money.yen(afterDiscount.amount().add(adjusted));
        Money newTax = taxExempt ? Money.zero() : newTaxable.multiply(taxRate);
        Money newTotal = newTaxable.add(newTax);

        appender.append(new InvoiceAdjustedEvent(invoiceId,
                // 取り消しそのものにも識別子を与える。**これも調整の 1 本**なので、
                // 番号の無い行を明細に積まない。
                reversalIdOf(command.adjustmentId()), command.adjustmentId(),
                original.negate(), command.reason(), null, adjusted,
                round(newTax), round(newTotal),
                baseAmount.currency(), command.reversedBy(), clock.instant()));
    }

    /**
     * 請求書を発行する（US23 §受入基準 1）。
     *
     * <p><b>支払期限は集約が決める</b>（不変条件 3。発行日 + 30 日）。コマンドで
     * 受け取ると、画面が期限を自由に決められることになる。</p>
     *
     * <p><b>「今日」は業務タイムゾーンで決める。</b> UTC で判断すると、時差の分
     * だけ発行日が前日になる時間帯ができる。</p>
     *
     * <p><b>取消からは再発行しない</b>（不変条件 6）。出し直すときは新規に発行する。</p>
     */
    @CommandHandler
    public void issue(IssueInvoiceCommand command, EventAppender appender, Clock clock) {
        if (invoiceId == null) {
            throw new IllegalTransition("請求書 " + command.invoiceId() + " がありません");
        }
        if (!status.acceptsIssue()) {
            throw new IllegalTransition(
                    "状態 " + status.label() + " の請求書は発行できません"
                            + (status == BillingStatus.VOID
                                    ? "（取り消した請求書は再発行しません。新規に発行してください）"
                                    : ""));
        }
        requireText(command.issuedBy(), "発行した人は必須です");

        LocalDate issuedOn = LocalDate.ofInstant(clock.instant(),
                BusinessClockConfiguration.BUSINESS_ZONE);
        appender.append(new InvoiceIssuedEvent(invoiceId, bookingId, shipperId,
                round(currentTotal()), baseAmount.currency(),
                issuedOn, PaymentTerm.dueOn(issuedOn),
                command.issuedBy(), clock.instant()));
    }

    /**
     * 入金を記録する（US23 §受入基準 3・4）。
     *
     * <p><b>決済機関との接続はスコープ外</b>（計画の注 N9）。経理担当者が入金
     * 明細を見て記録する。<b>{@code paidAt} は必須</b>（不変条件 5）——いつの
     * 入金かが分からない記録は、精算の証跡として使えない。</p>
     *
     * <p><b>発行していない請求書には記録しない。</b> 金額と支払期限が確定する
     * のは発行のときで、その前の入金は「何に対する入金か」が決まらない。</p>
     *
     * <p><b>一部入金は扱わない。</b> 請求額と違う額を受け取ったら断る——黙って
     * 入金済にすると、残りが誰にも見えないまま精算が終わる。</p>
     */
    @CommandHandler
    public void recordPayment(RecordPaymentCommand command, EventAppender appender,
            Clock clock) {
        if (invoiceId == null) {
            throw new IllegalTransition("請求書 " + command.invoiceId() + " がありません");
        }
        if (!status.acceptsPayment()) {
            throw new IllegalTransition(
                    "状態 " + status.label() + " の請求書には入金を記録できません");
        }
        requireText(command.paymentId(), "入金の識別子は必須です");
        requireText(command.recordedBy(), "記録した人は必須です");
        if (command.paidAt() == null) {
            throw new BusinessRuleViolation("入金日時は必須です");
        }
        if (command.amount() == null
                || command.amount().compareTo(round(currentTotal())) != 0) {
            // **黙って入金済にしない。** 残りが誰にも見えないまま精算が終わる。
            throw new BusinessRuleViolation(
                    "入金額が請求額と違います: " + command.amount()
                            + "（請求額 " + round(currentTotal()) + "）");
        }

        appender.append(new PaymentRecordedEvent(invoiceId, command.paymentId(),
                bookingId, shipperId, command.amount(), baseAmount.currency(),
                command.paidAt(), command.recordedBy(), clock.instant()));
    }

    /**
     * 記録した入金を取り消す（UC18 / US23。IT15 引き継ぎ 3）。
     *
     * <p><b>請求書の取消とは別の操作である。</b> 請求書は正しく、入金の記録だけが
     * 誤っている——取り違え・二重記録。請求書は<b>請求済に戻り</b>、督促の対象に
     * 戻る。マニュアル 17 章が「いまのところ運用で引き取る」と書いていたものを
     * 業務の操作にする。</p>
     *
     * <p><b>どの入金かを見る。</b> 状態だけで通すと、取り消したのがどの入金か
     * 残らない。</p>
     *
     * <p><b>取り消したあとは記録し直せる。</b> 正しい入金を入れ直すのが目的で、
     * 請求書を殺すのが目的ではない。</p>
     */
    @CommandHandler
    public void voidPayment(VoidPaymentCommand command, EventAppender appender, Clock clock) {
        if (invoiceId == null) {
            throw new IllegalTransition("請求書 " + command.invoiceId() + " がありません");
        }
        if (status != BillingStatus.PAID) {
            throw new IllegalTransition(
                    "状態 " + status.label() + " の請求書に取り消せる入金はありません");
        }
        requireText(command.reason(), "取消の理由は必須です");
        requireText(command.voidedBy(), "取り消した人は必須です");
        if (!java.util.Objects.equals(paymentId, command.paymentId())) {
            throw new BusinessRuleViolation(
                    "記録されている入金と違います: " + command.paymentId()
                            + "（記録されているのは " + paymentId + "）");
        }

        appender.append(new PaymentVoidedEvent(invoiceId, command.paymentId(), bookingId,
                command.reason(), command.voidedBy(), clock.instant()));
    }

    /**
     * 請求書を取り消す（UC18）。
     *
     * <p><b>入金済は取り消さない。</b> 決着したものを動かすと、入金の事実と
     * 請求書の状態が食い違う。</p>
     */
    @CommandHandler
    public void voidInvoice(VoidInvoiceCommand command, EventAppender appender, Clock clock) {
        if (invoiceId == null) {
            throw new IllegalTransition("請求書 " + command.invoiceId() + " がありません");
        }
        if (!status.acceptsVoid()) {
            throw new IllegalTransition(
                    "状態 " + status.label() + " の請求書は取り消せません");
        }
        requireText(command.reason(), "取消の理由は必須です");
        requireText(command.voidedBy(), "取り消した人は必須です");

        appender.append(new InvoiceVoidedEvent(invoiceId, bookingId, command.reason(),
                command.voidedBy(), clock.instant()));
    }

    /**
     * 支払期限を過ぎているか（不変条件 4・US23 §受入基準 5）。
     *
     * <p><b>列に持たない。</b> 持つと、日付が変わるたびに全件を書き換えること
     * になり、書き換えそこねた行が静かに未払いから漏れる。</p>
     *
     * <p><b>期限当日は超過ではない。</b> 当日中に入金されることはふつうにある。</p>
     *
     * <p><b>{@code today} は業務タイムゾーンで決める</b>（呼ぶ側の責任）。
     * UTC で判断すると、時差の分だけ 1 日早く督促が飛ぶ時間帯ができる。</p>
     */
    public boolean overdue(LocalDate today) {
        // **判定は 1 か所**（PaymentTerm）。一覧の SQL と別々に書くと、片方だけが
        // 正しくてもう片方が誤りを素通りさせる。
        return PaymentTerm.overdue(status, dueOn, today);
    }

    @EventSourcingHandler
    void on(InvoiceIssuedEvent event) {
        this.status = BillingStatus.INVOICED;
        this.dueOn = event.dueOn();
    }

    @EventSourcingHandler
    void on(PaymentRecordedEvent event) {
        this.status = BillingStatus.PAID;
        this.paymentId = event.paymentId();
    }

    @EventSourcingHandler
    void on(PaymentVoidedEvent event) {
        // **請求済に戻る。** 入金が無かったことになるので、督促の対象にも戻る。
        this.status = BillingStatus.INVOICED;
        this.paymentId = null;
    }

    @EventSourcingHandler
    void on(InvoiceVoidedEvent event) {
        this.status = BillingStatus.VOID;
    }

    @EventSourcingHandler
    void on(InvoiceCalculatedEvent event) {
        this.invoiceId = event.invoiceId();
        this.status = BillingStatus.CALCULATED;
        this.baseAmount = Money.yen(event.baseAmount());
        this.discountAmount = Money.yen(event.discountAmount());
        this.adjustmentTotal = BigDecimal.ZERO;
        this.taxExempt = event.taxExempt();
        this.taxRate = event.taxRate();
        this.bookingId = event.bookingId();
        this.shipperId = event.shipperId();
    }

    @EventSourcingHandler
    void on(InvoiceAdjustedEvent event) {
        this.adjustmentTotal = event.adjustmentTotal();
        // **復元では検査しない。** 識別子の無かったころの記録も読めなければ
        // ならない（不変条件を足しても、既存の記録が壊れてはいけない）。
        if (event.reversedAdjustmentId() != null) {
            reversibleAdjustments.remove(event.reversedAdjustmentId());
        } else if (event.adjustmentId() != null) {
            reversibleAdjustments.put(event.adjustmentId(), event.amount());
        }
    }

    private InvoiceCalculatedEvent.LineItem item(LineItemType type, String description,
            Money amount) {
        return new InvoiceCalculatedEvent.LineItem(type.name(), description,
                amount.roundToUnit().amount(), amount.currency(), null);
    }

    private String discountDescription(CalculateInvoiceCommand command) {
        String contract = command.contractNumber() == null ? ""
                : "・" + command.contractNumber();
        return LineItemType.DISCOUNT.label() + "（"
                + command.discountRate().percentage().toPlainString() + "%"
                + contract + "）";
    }

    /**
     * 取り消しの識別子。
     *
     * <p><b>36 文字に収める。</b> 列は {@code VARCHAR(36)} で、末尾に足すと
     * あふれる——集約は受け付けるので、投影が退避されるまで気づけない
     * （IT13 の請求書 ID と同じ形）。接頭辞を差し替えれば長さは変わらない。</p>
     *
     * <p><b>元の識別子から導く。</b> 採番すると、同じ取り消しが 2 度届いたときに
     * 別の行として積まれる。</p>
     */
    private static String reversalIdOf(String adjustmentId) {
        if (adjustmentId.startsWith("ADJ-")) {
            return "REV-" + adjustmentId.substring("ADJ-".length());
        }
        // 形の違う識別子。**切り詰めて衝突させない**——32 文字までに収める。
        String body = adjustmentId.length() > 32
                ? adjustmentId.substring(adjustmentId.length() - 32) : adjustmentId;
        return "REV-" + body;
    }

    /**
     * いまの請求額。
     *
     * <p><b>持たずに数え直す。</b> 合計を状態に持つと、調整のたびに 2 か所を
     * 直すことになる（集約と投影）。数え方は算出・調整と同じ 1 本である。</p>
     */
    private Money currentTotal() {
        Money afterDiscount = baseAmount.subtract(discountAmount);
        Money taxable = Money.yen(afterDiscount.amount().add(adjustmentTotal));
        Money tax = taxExempt ? Money.zero() : taxable.multiply(taxRate);
        return taxable.add(tax);
    }

    private static BigDecimal round(Money money) {
        return money.roundToUnit().amount();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation(message);
        }
    }
}
