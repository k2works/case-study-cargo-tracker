package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billing.domain.model.Adjustment;
import com.example.cargotracker.billing.domain.model.BillingBookingId;
import com.example.cargotracker.billing.domain.model.BillingShipperId;
import com.example.cargotracker.billing.domain.model.ChargeStatus;
import com.example.cargotracker.billing.domain.model.DiscountRate;
import com.example.cargotracker.billing.domain.model.Invoice;
import com.example.cargotracker.billing.domain.model.InvoiceId;
import com.example.cargotracker.billing.domain.model.InvoiceParties;
import com.example.cargotracker.billing.domain.model.InvoiceType;
import com.example.cargotracker.billing.domain.model.Money;
import com.example.cargotracker.billing.domain.repository.InvoiceRepository;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 精算書の永続化（US21 / US22）。
 *
 * <p><strong>SQL の正しさを検証する唯一の場所である</strong>（ADR-003）。
 * H2 では書かない。
 *
 * <p>確かめるのは往復だけではない。<strong>二重請求を DB が拒むこと</strong>、
 * <strong>確定済みは更新できないこと</strong>、
 * <strong>調整を持たない行も読み戻せること</strong>まで見る。
 */
@DisplayName("精算書の永続化（US21 / US22）")
class InvoiceRepositoryTest extends PostgreSQLIntegrationTestBase {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.1000");

    @Autowired
    private InvoiceRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Invoice 算出する(BigDecimal base, BigDecimal rate, boolean corporate) {
        return Invoice.calculate(
                InvoiceParties.of(
                        repository.nextInvoiceId(),
                        new BillingBookingId(UUID.randomUUID().toString()),
                        new BillingShipperId(UUID.randomUUID().toString(), corporate)),
                Money.yen(base),
                rate == null ? null : DiscountRate.of(rate),
                TAX_RATE);
    }

    /** 保存して読み戻すと、丸め後の金額と根拠がそのまま残る。 */
    @Test
    void 保存して読み戻せる() {
        Invoice invoice = 算出する(new BigDecimal("100003"), new BigDecimal("0.15"), true);
        repository.save(invoice);

        Invoice found = repository.findByInvoiceId(invoice.invoiceId()).orElseThrow();

        assertThat(found.baseAmount().value()).isEqualTo(new BigDecimal("100003"));
        assertThat(found.discountRate().value())
                .as("US22 の「割引計算の根拠」。契約が翌月変わっても先月の率が残る")
                .isEqualByComparingTo(new BigDecimal("0.1500"));
        assertThat(found.discountAmount().value()).isEqualTo(new BigDecimal("15001"));
        assertThat(found.taxRate())
                .as("税率も残る。金額だけでは根拠を再現できない")
                .isEqualByComparingTo(TAX_RATE);
        assertThat(found.taxAmount().value()).isEqualTo(new BigDecimal("8500"));
        assertThat(found.totalAmount().value()).isEqualTo(new BigDecimal("93502"));
        assertThat(found.chargeStatus()).isEqualTo(ChargeStatus.DRAFT);
    }

    /**
     * <strong>契約割引率が 0% の法人でも、読み戻すと法人のままである</strong>（C6）。
     *
     * <p>法人かどうかを<strong>割引率から逆算していた</strong>。率が 0% の法人
     * ——契約はあるが割引条件がまだ登録されていない荷主——は、読み戻すと個人になる。
     *
     * <p><strong>0% は「法人でない」ではない。</strong> 法人契約の有無は
     * 取引条件であり、割引の結果から復元してよい事実ではない
     * （「集約状態の再導出禁止」の型）。
     */
    @Test
    void 割引率がゼロの法人も法人として読み戻せる() {
        Invoice invoice = 算出する(new BigDecimal("1000"), BigDecimal.ZERO, true);
        repository.save(invoice);

        Invoice found = repository.findByInvoiceId(invoice.invoiceId()).orElseThrow();

        assertThat(found.corporate())
                .as("**割引率 0%% と個人荷主を混同しない**")
                .isTrue();
    }

    /** 予約と種別からも引ける（<strong>二重請求の判定に使う</strong>）。 */
    @Test
    void 予約と種別から引ける() {
        Invoice invoice = 算出する(new BigDecimal("1000"), null, false);
        repository.save(invoice);

        assertThat(repository.findByBookingId(
                invoice.cargoBookingId(), InvoiceType.TRANSPORT))
                .isPresent();
        assertThat(repository.findByBookingId(
                invoice.cargoBookingId(), InvoiceType.CANCELLATION))
                .as("キャンセル料はまだ無い。**種別を無視して引かない**")
                .isEmpty();
    }

    /**
     * <strong>同じ予約に同じ種別の請求書を二重に作れない。</strong>
     *
     * <p>DB の一意制約が最後の砦である。<strong>業務の言葉で拒むのは
     * {@code BillableCargo} の仕事</strong>だが、そこを通らない経路でも防ぐ。
     *
     * <p><strong>数えるのではなく 2 枚目を入れて確かめる</strong>（US30 で UK を
     * 組み替えたため）。件数を数えるだけでは、制約が消えていても緑になる。
     */
    @Test
    void 同じ予約に同じ種別の請求書を二重に作れない() {
        Invoice first = 算出する(new BigDecimal("1000"), null, false);
        repository.save(first);

        Invoice second = Invoice.calculate(
                InvoiceParties.of(
                        repository.nextInvoiceId(),
                        first.cargoBookingId(),
                        new BillingShipperId(UUID.randomUUID().toString(), false)),
                Money.yen(new BigDecimal("2000")), null, TAX_RATE);

        assertThatThrownBy(() -> repository.save(second))
                .as("**制約が最後の砦である**")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /**
     * <strong>同じ予約に輸送料金とキャンセル料を並べられる</strong>（US30。X1）。
     *
     * <p>V1 の「予約ごとに 1 枚」では、輸送中にキャンセルした荷主へ
     * <strong>キャンセル料を請求する手段がシステムに無い</strong>。
     */
    @Test
    void 同じ予約に輸送料金とキャンセル料を並べられる() {
        Invoice transport = 算出する(new BigDecimal("100000"), null, false);
        repository.save(transport);

        Invoice fee = Invoice.cancellationFee(
                InvoiceParties.of(
                        repository.nextInvoiceId(),
                        transport.cargoBookingId(),
                        transport.shipperId()),
                transport.baseAmount(), new BigDecimal("0.50"), TAX_RATE);
        repository.save(fee);

        Invoice found = repository.findByBookingId(
                transport.cargoBookingId(), InvoiceType.CANCELLATION).orElseThrow();
        assertThat(found.invoiceType()).isEqualTo(InvoiceType.CANCELLATION);
        assertThat(found.baseAmount().value())
                .as("基本料金の 50%。**割引も調整も適用しない**")
                .isEqualTo(new BigDecimal("50000"));
        assertThat(repository.findByBookingId(
                transport.cargoBookingId(), InvoiceType.TRANSPORT).orElseThrow()
                .baseAmount().value())
                .as("輸送料金の請求書は影響を受けない")
                .isEqualTo(new BigDecimal("100000"));
    }

    /** 料金調整を保存して読み戻せる。 */
    @Test
    void 料金調整を保存して読み戻せる() {
        Invoice invoice = 算出する(new BigDecimal("100000"), null, false);
        invoice.adjust(new Adjustment(
                Money.yen(new BigDecimal("10000")),
                Money.yen(new BigDecimal("3000")),
                "遅延による減額と代替輸送費"));
        repository.save(invoice);

        Invoice found = repository.findByInvoiceId(invoice.invoiceId()).orElseThrow();

        assertThat(found.hasAdjustment()).isTrue();
        assertThat(found.adjustment().reason()).isEqualTo("遅延による減額と代替輸送費");
        assertThat(found.totalAmount().value()).isEqualTo(new BigDecimal("102300"));
    }

    /**
     * <strong>調整を持たない行も読み戻せる。</strong>
     *
     * <p>調整していない精算書のほうが多い。新しい列で既存の形を読めなくしない。
     */
    @Test
    void 調整の無い精算書も読み戻せる() {
        Invoice invoice = 算出する(new BigDecimal("1000"), null, false);
        repository.save(invoice);

        assertThat(repository.findByInvoiceId(invoice.invoiceId()).orElseThrow().hasAdjustment())
                .isFalse();
    }

    /** 確定を保存できる。 */
    @Test
    void 確定を保存できる() {
        Invoice invoice = 算出する(new BigDecimal("1000"), null, false);
        repository.save(invoice);
        invoice.confirmCharge();

        assertThat(repository.update(invoice)).isTrue();
        assertThat(repository.findByInvoiceId(invoice.invoiceId()).orElseThrow().isConfirmed())
                .isTrue();
    }

    /**
     * <strong>確定済みは更新できない。</strong>
     *
     * <p>ドメインの守り（{@code requireDraft}）と<strong>同じ条件を SQL にも書いている</strong>。
     * 集約を通らない経路が生まれても、確定後の金額は動かない。
     * <strong>壊してみる</strong>: 確定済みの行に対する UPDATE は 0 件になる。
     */
    @Test
    void 確定済みは更新できない() {
        Invoice invoice = 算出する(new BigDecimal("1000"), null, false);
        repository.save(invoice);
        invoice.confirmCharge();
        repository.update(invoice);

        Invoice confirmed = repository.findByInvoiceId(invoice.invoiceId()).orElseThrow();

        assertThat(repository.update(confirmed))
                .as("確定済みへの更新は 0 件。SQL の WHERE が守る")
                .isFalse();
    }

    /**
     * <strong>他の担当者が先に更新したら気づく</strong>（レビュー H4）。
     *
     * <p>月初は複数の経理担当者が同時に締めを回す。<strong>黙って上書きすると、
     * 一方の料金調整が消えたまま確定される。</strong>
     *
     * <p><strong>本 IT には競合を実行したテストが 1 件も無かった。</strong>
     * 楽観的ロックは「入れたこと」ではなく「働くこと」を確かめないと、
     * 在庫の無い安全装置になる。
     */
    @Test
    void 他の担当者が先に更新すると拒まれる() {
        Invoice invoice = 算出する(new BigDecimal("100000"), null, false);
        repository.save(invoice);

        // 2 人が同じ精算書を開く
        Invoice first = repository.findByInvoiceId(invoice.invoiceId()).orElseThrow();
        Invoice second = repository.findByInvoiceId(invoice.invoiceId()).orElseThrow();

        first.adjust(new Adjustment(
                Money.yen(new BigDecimal("1000")), Money.zeroYen(), "先に入れた減額"));
        assertThat(repository.update(first))
                .as("先に更新した側は通る")
                .isTrue();

        second.adjust(new Adjustment(
                Money.yen(new BigDecimal("2000")), Money.zeroYen(), "後から入れた減額"));
        assertThat(repository.update(second))
                .as("後から更新した側は拒まれる。黙って上書きしない")
                .isFalse();

        assertThat(repository.findByInvoiceId(invoice.invoiceId()).orElseThrow()
                .adjustment().reason())
                .as("先に入れた側が残る")
                .isEqualTo("先に入れた減額");
    }

    /**
     * <strong>採番はシーケンスに任せる。</strong>
     *
     * <p>MAX+1 を数えると、同時に 2 件発行したときに衝突する。
     */
    @Test
    void 精算書番号が重複しない() {
        InvoiceId first = repository.nextInvoiceId();
        InvoiceId second = repository.nextInvoiceId();

        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(first.value()).startsWith("INV-");
    }

    /**
     * <strong>割引率の上限を DB も守る。</strong>
     *
     * <p>画面とドメインを通らない経路（移行・手作業）でも 30% を超えられない。
     * <strong>V1 から在る制約である</strong> — 本 IT で足したのではなく、
     * ドメインの不変条件と DB が一致していることを確かめている。
     */
    @Test
    void 割引率の上限をDBも守る() {
        Invoice invoice = 算出する(new BigDecimal("1000"), null, false);
        repository.save(invoice);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.check_constraints
                 WHERE constraint_name = 'chk_invoice_discount_rate'
                """, Integer.class))
                .isEqualTo(1);
    }
}
