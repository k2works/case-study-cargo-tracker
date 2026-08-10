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

    /**
     * <strong>発行と入金を保存して読み戻せる</strong>（US23）。
     *
     * <p><strong>支払期限は保存された値である。</strong> 読み戻すたびに
     * 「発行日 + N 日」で計算し直すと、設定を変えた日に既発行分の期限が動く。
     */
    @Test
    void 発行と入金を保存して読み戻せる() {
        Invoice invoice = 算出する(new BigDecimal("1000"), null, false);
        invoice.confirmCharge();
        repository.save(invoice);

        Invoice confirmed = repository.findByInvoiceId(invoice.invoiceId()).orElseThrow();
        confirmed.issue(new com.example.cargotracker.billing.domain.model.Issuance(
                java.time.Instant.parse("2026-05-01T00:00:00Z"),
                java.time.LocalDate.of(2026, 5, 31)));
        assertThat(repository.updateSettlement(confirmed)).isTrue();

        Invoice issued = repository.findByInvoiceId(invoice.invoiceId()).orElseThrow();
        assertThat(issued.isIssued()).isTrue();
        assertThat(issued.issuance().dueDate())
                .as("**支払期限は保存された値である**（読み戻しで計算し直さない）")
                .isEqualTo(java.time.LocalDate.of(2026, 5, 31));
        assertThat(issued.paymentStatus())
                .isEqualTo(com.example.cargotracker.billing.domain.model
                        .PaymentStatus.PENDING);

        issued.confirmPayment(new com.example.cargotracker.billing.domain.model.Payment(
                issued.totalAmount(), java.time.Instant.parse("2026-05-20T00:00:00Z"),
                com.example.cargotracker.billing.domain.model.PaymentMethod.BANK_TRANSFER,
                "TX-0001"));
        assertThat(repository.savePayment(issued)).isTrue();

        Invoice paid = repository.findByInvoiceId(invoice.invoiceId()).orElseThrow();
        assertThat(paid.paymentStatus())
                .isEqualTo(com.example.cargotracker.billing.domain.model
                        .PaymentStatus.CONFIRMED);
        assertThat(paid.payment().paidAmount().value())
                .as("**入金額が残る。** いくら入ったかは帳簿の照合に要る")
                .isEqualTo(new BigDecimal("1100"));
        assertThat(paid.payment().transactionReference()).isEqualTo("TX-0001");
    }

    /**
     * <strong>確定していない請求書は発行できない</strong>（DB でも守る。US23）。
     *
     * <p>ドメインが拒むことは単体で確かめている。<strong>集約を通らない経路が
     * 生まれても下書きが発行されない</strong>ことを、SQL の側でも見る。
     */
    @Test
    void 下書きの請求書は精算の更新ができない() {
        Invoice draft = 算出する(new BigDecimal("1000"), null, false);
        repository.save(draft);

        // ドメインを通さずに（＝確定を経ずに）精算だけ書こうとする
        Invoice loaded = repository.findByInvoiceId(draft.invoiceId()).orElseThrow();

        assertThat(repository.updateSettlement(loaded))
                .as("**WHERE の charge_status = 'CONFIRMED' が最後の砦である**")
                .isFalse();
    }

    /**
     * <strong>確定していない請求書は未入金以外になれない</strong>（ADR-017 の CHECK）。
     *
     * <p>ADR-017 は「料金の状態と支払いの状態は別の軸」と述べて分けた。
     * <strong>分けた後の不変条件を口約束のままにしない</strong> — DB の制約で守る。
     */
    @Test
    void 確定前に入金確認済みへ動かすことはDBが拒む() {
        Invoice draft = 算出する(new BigDecimal("1000"), null, false);
        repository.save(draft);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE invoice SET payment_status = 'CONFIRMED' WHERE invoice_number = ?",
                draft.invoiceId().value()))
                .as("**口約束ではなく制約で守る**")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** 予約からも引ける（<strong>二重請求の判定に使う</strong>）。 */
    @Test
    void 予約から引ける() {
        Invoice invoice = 算出する(new BigDecimal("1000"), null, false);
        repository.save(invoice);

        assertThat(repository.findByBookingId(invoice.cargoBookingId()))
                .isPresent();
    }

    /**
     * <strong>同じ予約に二重に請求書を作れない。</strong>
     *
     * <p>DB の一意制約が最後の砦である。<strong>業務の言葉で拒むのは
     * {@code BillableCargo} の仕事</strong>だが、そこを通らない経路でも防ぐ。
     */
    @Test
    void 同じ予約に二重に請求書を作れない() {
        Invoice first = 算出する(new BigDecimal("1000"), null, false);
        repository.save(first);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice WHERE booking_id = ?",
                Integer.class, UUID.fromString(first.cargoBookingId().value()));
        assertThat(count).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                 WHERE table_name = 'invoice' AND constraint_type = 'UNIQUE'
                """, Integer.class))
                .as("invoice_number と booking_id の 2 本")
                .isEqualTo(2);
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
