package com.example.handlingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.handlingms.domain.model.aggregates.CustomsDeclaration;
import com.example.handlingms.domain.model.valueobjects.CargoBookingId;
import com.example.handlingms.domain.model.valueobjects.DeclarationNumber;
import com.example.handlingms.domain.model.valueobjects.HandlingTrackingNumber;
import com.example.handlingms.domain.repository.CustomsDeclarationRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 通関の待ち行列から、シミュレーション由来を外す（TD-02・IT16）。
 *
 * <p><strong>追跡管理者の朝の仕事は「未決着を上から片付ける」ことである。</strong>
 * ここに架空の申告が並ぶと、実在の貨物が後ろへ押し出される——[ADR-030] 決定 3 が
 * 守ろうとしているのは、まさにこの一覧の信用である。
 *
 * <p><strong>件数にも同じ条件を掛ける。</strong>片方だけに掛けると「12 件あります」と
 * 出るのに開くと 3 件、という形になる（予約一覧で同じ形を踏んでいる）。
 */
@DisplayName("通関の待ち行列からの除外")
class SimulatedCustomsExclusionIntegrationTest extends HandlingIntegrationTestBase {

    private static final Instant DECLARED_AT = Instant.parse("2027-10-01T00:00:00Z");

    /** 他の検査の「最新の申告」を書き換えないよう、この検査だけの予約 ID を使う。 */
    private static final String REAL_BOOKING = "BKG-2026009101";

    private static final String SIM_BOOKING = "BKG-2026009102";

    @Autowired
    private CustomsDeclarationRepository declarations;

    private void declare(String number, String bookingId, String trackingNumber,
            boolean simulated) {
        declarations.save(CustomsDeclaration.declare(
                DeclarationNumber.of(number), CargoBookingId.of(bookingId),
                HandlingTrackingNumber.of(trackingNumber), DECLARED_AT, "申告", "handler01",
                simulated));
    }

    @Test
    @DisplayName("待ち行列に、シミュレーション由来の申告は出ない")
    void keepsSimulatedOutOfTheQueue() {
        declare("DEC-S0001", REAL_BOOKING, "TRK-20261001-9101", false);
        declare("DEC-S0002", SIM_BOOKING, "TRK-20261001-9102", true);

        assertThat(declarations.search(null, null, null, true, 100))
                .extracting(declaration -> declaration.cargoBookingId().value())
                .as("通関の待ち行列に架空の申告が混ざっている")
                .contains(REAL_BOOKING)
                .doesNotContain(SIM_BOOKING);
    }

    @Test
    @DisplayName("件数にも同じ条件が掛かる")
    void appliesTheSameFilterToTheCount() {
        declare("DEC-S0003", REAL_BOOKING, "TRK-20261001-9103", false);
        declare("DEC-S0004", SIM_BOOKING, "TRK-20261001-9104", true);

        long listed = declarations.search(null, null, null, true, 1000).size();

        assertThat(declarations.count(null, null, null, true))
                .as("件数と一覧の中身が食い違う")
                .isEqualTo(listed);
    }

    /**
     * <strong>名指しの照会では外さない。</strong>外すと、シミュレーション自身が
     * 引取のガードを越えられず、業務を進められなくなる（[ADR-030] 決定 1）。
     */
    @Test
    @DisplayName("追跡番号を指定した照会では、シミュレーション由来も返る")
    void namedLookupStillFindsSimulated() {
        declare("DEC-S0005", SIM_BOOKING, "TRK-20261001-9105", true);

        assertThat(declarations.search(null, "TRK-20261001-9105", null, false, 100))
                .as("名指しで引いたのに返らない。シミュレーションが業務を進められなくなる")
                .isNotEmpty();
    }
}
