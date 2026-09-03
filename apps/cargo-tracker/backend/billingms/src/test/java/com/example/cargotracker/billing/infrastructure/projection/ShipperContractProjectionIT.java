package com.example.cargotracker.billing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.infrastructure.persistence.ShipperContractSnapshotMapper;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 契約スナップショット（US03 §受入基準 4）。
 *
 * <p>billingms は請求のたびに bookingms へ問い合わせない。同期問い合わせにすると、
 * bookingms が落ちている間は請求書が作れなくなる。契約イベントを購読して自分の
 * 読み取りモデルに写す。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShipperContractProjectionIT extends AbstractAxonIntegrationTest {

    @Autowired
    private ShipperContractProjection projection;

    @Autowired
    private ShipperContractSnapshotMapper snapshots;

    private ShipperRegisteredEvent corporate(String shipperId, String discountRate) {
        return new ShipperRegisteredEvent(shipperId, "CORPORATE", "山田商事",
                shipperId + "@example.com", "03-0000-0000", "東京都港区", "CT-0001", discountRate);
    }

    @Test
    @DisplayName("法人の割引率と契約番号が写る")
    void projectsCorporateContract() {
        String id = "SHP-BILL-" + System.nanoTime();

        projection.on(corporate(id, "0.1000"));

        ShipperContractSnapshotMapper.SnapshotRow row = snapshots.find(id);
        assertThat(row).isNotNull();
        assertThat(row.discountRate()).isEqualByComparingTo(new BigDecimal("0.1000"));
        assertThat(row.contractNumber()).isEqualTo("CT-0001");
        assertThat(row.shipperType()).isEqualTo("CORPORATE");
    }

    @Test
    @DisplayName("個人は割引率も契約番号も持たない")
    void projectsIndividualWithoutContract() {
        String id = "SHP-BILL-IND-" + System.nanoTime();

        projection.on(new ShipperRegisteredEvent(id, "INDIVIDUAL", "山田 太郎",
                id + "@example.com", "03-0000-0000", "東京都港区", null, null));

        ShipperContractSnapshotMapper.SnapshotRow row = snapshots.find(id);
        assertThat(row.discountRate()).isNull();
        assertThat(row.contractNumber()).isNull();
    }

    @Test
    @DisplayName("同じイベントが 2 度届いても行は増えない")
    void isIdempotent() {
        // 少なくとも 1 回配送なので、同じイベントが 2 度届きうる。リプレイでも同じ。
        String id = "SHP-BILL-DUP-" + System.nanoTime();
        int before = snapshots.count();

        projection.on(corporate(id, "0.1000"));
        projection.on(corporate(id, "0.2000"));

        assertThat(snapshots.count()).isEqualTo(before + 1);
        assertThat(snapshots.find(id).discountRate())
                .as("あとから届いた値が残る")
                .isEqualByComparingTo(new BigDecimal("0.2000"));
    }

    @Test
    @DisplayName("鍵を破棄した荷主でも投影は止まらない")
    void survivesShreddedPersonalData() {
        // 鍵破棄後のリプレイでは氏名が null で届く（ADR-0003）。NOT NULL にすると
        // ここで投影が止まり、以降のイベントが 1 件も反映されなくなる。
        String id = "SHP-BILL-SHRED-" + System.nanoTime();

        projection.on(new ShipperRegisteredEvent(id, "CORPORATE", null, null, null, null,
                "CT-0001", "0.1000"));

        ShipperContractSnapshotMapper.SnapshotRow row = snapshots.find(id);
        assertThat(row.shipperName()).isNull();
        assertThat(row.contractNumber())
                .as("契約は個人情報ではないので残る。請求はこれで割引を当てる")
                .isEqualTo("CT-0001");
    }
}
