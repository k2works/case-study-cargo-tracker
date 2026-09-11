package com.example.cargotracker.billing.domain.model.aggregates;

import com.example.cargotracker.billing.domain.model.commands.AdjustInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.CalculateInvoiceCommand;
import com.example.cargotracker.billing.domain.model.events.InvoiceAdjustedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.domain.model.valueobjects.BillingStatus;
import com.example.cargotracker.billing.domain.model.valueobjects.FreightCharge;
import com.example.cargotracker.billing.domain.model.valueobjects.LineItemType;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTable;
import com.example.cargotracker.billing.domain.service.DiscountPolicy;
import com.example.cargotracker.billing.domain.service.FreightChargeCalculator;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
 * <p><b>期限超過（{@code overdue}）は作らない。</b> 期限が決まるのは発行のとき
 * （不変条件 3・US23・IT14）で、入力経路の無い述語をいま作ると**壊しても赤に
 * ならない**（IT12 の「常に 0 の列」と同型）。</p>
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
     * 調整のたびに税を数え直すのに要る。
     *
     * <p><b>税額と合計そのものは持たない。</b> 調整のたびに数え直すので、
     * 持っても読まない——<b>読む側の無い状態を先に持たない</b>（IT12 の
     * 「常に 0 の列」と同じ形）。金額は投影が持ち、画面はそちらを読む。</p>
     */
    private boolean taxExempt;

    /** 税率。**算出時のものを覚えておく**——あとで料率が変わっても請求書は変わらない。 */
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
                round(base), round(discount), round(tax), round(total), base.currency(),
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

        appender.append(new InvoiceAdjustedEvent(invoiceId, command.amount(), command.reason(),
                command.basisExceptionId(), adjusted, round(newTax), round(newTotal),
                baseAmount.currency(), command.adjustedBy(), clock.instant()));
    }

    @EventSourcingHandler
    void on(InvoiceCalculatedEvent event) {
        this.invoiceId = event.invoiceId();
        this.status = BillingStatus.CALCULATED;
        this.baseAmount = Money.yen(event.baseAmount());
        this.discountAmount = Money.yen(event.discountAmount());
        this.adjustmentTotal = BigDecimal.ZERO;
        this.taxExempt = event.taxAmount().signum() == 0;
        this.taxRate = deriveTaxRate(event);
    }

    @EventSourcingHandler
    void on(InvoiceAdjustedEvent event) {
        this.adjustmentTotal = event.adjustmentTotal();
    }

    /**
     * 算出時の税率。
     *
     * <p>輸出免税なら 0。そうでなければ「税額 ÷ 課税対象」で導く——<b>料率表を
     * 復元のたびに読まない</b>（あとで料率が変わっても、出した請求書は変わらない）。</p>
     */
    private static BigDecimal deriveTaxRate(InvoiceCalculatedEvent event) {
        BigDecimal taxable = event.baseAmount().subtract(event.discountAmount());
        if (event.taxAmount().signum() == 0 || taxable.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return event.taxAmount().divide(taxable, 4, java.math.RoundingMode.HALF_UP);
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

    private static BigDecimal round(Money money) {
        return money.roundToUnit().amount();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation(message);
        }
    }
}
