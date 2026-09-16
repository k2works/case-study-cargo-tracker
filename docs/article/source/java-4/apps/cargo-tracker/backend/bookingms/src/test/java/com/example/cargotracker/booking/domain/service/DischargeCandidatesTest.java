package com.example.cargotracker.booking.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.valueobjects.CancellationDecision;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 陸揚げ地の選択肢（US30 §受入基準 5 / 不変条件 9-2）。
 *
 * <p><b>画面の選択肢と集約の断りは同じものである。</b> ここで確かめるのは
 * 「候補に入っているものは必ず承認できる」こと——別々に書くと、画面に出ている
 * のに押すと断られる港が生まれる（IT5 のレビューで一度出た形）。</p>
 */
class DischargeCandidatesTest {

    private static final Instant AT = Instant.parse("2026-09-25T02:00:00Z");

    /** 東京 → シンガポール → ニューヨーク。 */
    private static final List<DischargeCandidates.Leg> ITINERARY = List.of(
            new DischargeCandidates.Leg("JPTYO", "SGSIN"),
            new DischargeCandidates.Leg("SGSIN", "USNYC"));

    @ParameterizedTest(name = "現在地 {0} → 候補 {1}")
    @CsvSource({
        // 東京で受領した直後。**積み港に居ることは通ったことではない**ので、
        // シンガポールもニューヨークも候補に残る。
        "JPTYO, JPTYO|SGSIN|USNYC",
        // シンガポールで降ろした。東京はもう戻らない。
        "SGSIN, SGSIN|USNYC",
        // まだ荷役が無い（現在地なし）。旅程の荷降し港だけ。
        ", SGSIN|USNYC",
        // **最終の荷降し港に着いた。** 先はもう無いので、候補は現在地 1 件だけに
        // なる——`skip(passed + 1)` の off-by-one はここでしか出ない
        // （IT15 のレビュー 中）。0 件になると承認そのものができなくなる。
        "USNYC, USNYC",
    })
    @DisplayName("現在地から先の港が候補になる（現在地が先頭）")
    void listsPortsAhead(String current, String expected) {
        assertThat(DischargeCandidates.of(ITINERARY, current))
                .extracting(port -> port.unLocode().value())
                .containsExactly(expected.split("\\|"));
    }

    @Test
    @DisplayName("区間が 1 本の旅程でも、候補は空にならない")
    void keepsAtLeastOneCandidateOnASingleLeg() {
        // 直航（乗り継ぎ無し）。降ろせるのは目的地だけだが、**0 件にはしない**
        // ——0 件だと承認できず、貨物が船の上に残り続ける。
        List<DischargeCandidates.Leg> direct =
                List.of(new DischargeCandidates.Leg("JPTYO", "USNYC"));

        assertThat(DischargeCandidates.of(direct, "JPTYO"))
                .extracting(port -> port.unLocode().value())
                .containsExactly("JPTYO", "USNYC");
        assertThat(DischargeCandidates.of(direct, "USNYC"))
                .as("目的地に着いたあとも、そこで降ろせる")
                .extracting(port -> port.unLocode().value())
                .containsExactly("USNYC");
    }

    @Test
    @DisplayName("候補に入っている港は、集約も必ず承認する（画面と集約が食い違わない）")
    void everyCandidateIsAccepted() {
        // **片方だけを見ると、食い違いは見つからない。** 候補を作る関数と、
        // 断る関数の両方を同じ材料で回す。
        for (String current : new String[] {"JPTYO", "SGSIN", null}) {
            List<Location> candidates = DischargeCandidates.of(ITINERARY, current);
            assertThat(candidates).as("候補が空だと承認そのものができない").isNotEmpty();
            for (Location candidate : candidates) {
                assertThat(CancellationDecision.approve(candidate.unLocode().value(), candidates, null,
                        "tracker01", AT).dischargeLocation())
                        .as("現在地 %s の候補 %s", current, candidate)
                        .isEqualTo(candidate);
            }
        }
    }

    @Test
    @DisplayName("現在地が旅程に無い（誤配）なら、全部の荷降し港を候補にする")
    void keepsAllPortsWhenMisrouted() {
        // どこまで進んだか分からない状態で候補を狭めると、実際に降ろせる港まで消える。
        assertThat(DischargeCandidates.of(ITINERARY, "GBLON"))
                .extracting(port -> port.unLocode().value())
                .containsExactly("GBLON", "SGSIN", "USNYC");
    }
}
