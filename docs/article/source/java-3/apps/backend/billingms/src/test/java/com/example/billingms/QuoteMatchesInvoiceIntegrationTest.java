package com.example.billingms;

import static com.example.billingms.ChargeFixtures.domesticSnapshotLegs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.billingms.application.internal.commandservices.CalculateChargeUseCase;
import com.example.billingms.application.internal.queryservices.QuoteChargeUseCase;
import com.example.billingms.application.internal.outboundservices.acl.BillableCargoSnapshot;
import com.example.billingms.application.internal.outboundservices.acl.BillingSnapshotFinder;
import com.example.billingms.domain.model.valueobjects.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 見積の概算料金と実料金が一致する（デモ 10・[ADR-028] 決定 6）。
 *
 * <p><strong>式が 1 か所にあることを、答えの一致で確かめる。</strong>「同じ関数を
 * 呼んでいる」ことをコードで確かめても、呼び出し側が前後で別の加工をしていれば
 * 数字はずれる——<strong>出てくる金額そのものを突き合わせる</strong>。
 *
 * <p>ずれると、営業担当者は毎回「見積はあくまで概算です」と言うことになり、
 * 見積の意味が消える。
 */
@SpringBootTest
@DisplayName("見積と実料金の一致")
class QuoteMatchesInvoiceIntegrationTest {

    @Autowired
    private QuoteChargeUseCase quote;

    @Autowired
    private CalculateChargeUseCase calculateCharge;

    @MockitoBean
    private BillingSnapshotFinder snapshots;

    /**
     * <strong>同じ条件なら同じ金額になる。</strong>
     *
     * <p>区間数・重量・貨物種別を変えても一致し続けることを見る——1 通りだけだと、
     * 偶然一致する実装（どちらも定数を返す）でも緑になる。
     */
    @ParameterizedTest(name = "{0} 区間・{1}kg・{2}")
    @CsvSource({"1,1000,GENERAL", "2,4200,GENERAL", "3,800,REFRIGERATED", "1,12000,HAZARDOUS"})
    @DisplayName("見積の概算料金は、同じ条件の実料金（基本料金）と一致する")
    void quotesTheSameAmountAsTheInvoice(int legCount, String weight, String cargoType) {
        BigDecimal weightKg = new BigDecimal(weight);
        when(snapshots.findBillable("BKG-2026000007")).thenReturn(Optional.of(
                new BillableCargoSnapshot("BKG-2026000007", "DELIVERED", "1", "丸紅商事", false,
                        false, null, weightKg, cargoType, "Tokyo", "JP", "Osaka", "JP",
                        legCount, domesticSnapshotLegs(legCount),
                        Instant.parse("2027-09-26T00:00:00Z"), null, null)));

        Money quoted = quote.quote(
                java.util.Collections.nCopies(legCount,
                        new QuoteChargeUseCase.QuoteLeg("DOMESTIC", "DOMESTIC")),
                weightKg, cargoType);
        Money invoiced = calculateCharge.calculate("BKG-2026000007").charge().baseAmount();

        assertThat(quoted)
                .as("見積と実料金が違う金額になっている。荷主に出した数字が守れない")
                .isEqualTo(invoiced);
    }

    /** 遠洋でも一致する。**地域区分が片方だけに効いていないこと**を見る。 */
    @Test
    @DisplayName("遠洋の経路でも、見積と実料金は一致する")
    void quotesTheSameAmountForOceanRoutes() {
        when(snapshots.findBillable("BKG-2026000008")).thenReturn(Optional.of(
                new BillableCargoSnapshot("BKG-2026000008", "DELIVERED", "2", "山田太郎", false, false,
                        null, new BigDecimal("4200"), "GENERAL",
                        "Tokyo", "JP", "Los Angeles", "US", 1,
                        List.of(new BillableCargoSnapshot.Leg("DOMESTIC", "OCEAN")),
                        Instant.parse("2027-09-26T00:00:00Z"), null, null)));

        Money quoted = quote.quote(
                List.of(new QuoteChargeUseCase.QuoteLeg("DOMESTIC", "OCEAN")),
                new BigDecimal("4200"), "GENERAL");
        Money invoiced = calculateCharge.calculate("BKG-2026000008").charge().baseAmount();

        assertThat(quoted).isEqualTo(invoiced);
    }
}
