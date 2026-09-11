package com.example.cargotracker.handling.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.infrastructure.persistence.CustomsDeclarationMapper;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsDeclarationMapper.CustomsDeclarationRow;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsStatusHistoryMapper;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CountOverdueCustomsHoldsQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsDeclarationView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsDeclarationsQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 督促の判定（US29 §受入基準 6）の境界。
 *
 * <p><b>時計を固定して境界を踏む。</b> 実時計で「十分に古い留置」を作ると、閾値を
 * 3 から 10 に変えても緑のままになる——日が経つほど値が離れるので、<b>判定を壊しても
 * 赤にならない</b>（IT12 レビュー 高）。ここは {@code Clock.fixed} と差し替えた
 * マッパーで 3 営業日と 4 営業日の 2 点を見る。</p>
 *
 * <p><b>一覧と件数が同じ判定であることも、判別する形で見る。</b> 「督促でない留置」を
 * 1 件混ぜないと、件数側が全 {@code HELD} を数える実装に戻しても緑になる。</p>
 */
class CustomsQueryHandlerTest {

    /** 2026-10-05（月）11:00 JST。ここを「今日」とする。 */
    private static final Instant NOW = Instant.parse("2026-10-05T02:00:00Z");

    /** 2026-09-30（水）に留置 → 10/1・2・5 の 3 営業日（土日を外す）。 */
    private static final Instant HELD_3_DAYS = Instant.parse("2026-09-30T02:00:00Z");

    /** 2026-09-29（火）に留置 → 9/30・10/1・2・5 の 4 営業日。 */
    private static final Instant HELD_4_DAYS = Instant.parse("2026-09-29T02:00:00Z");

    private static final Instant DECLARED = Instant.parse("2026-09-20T02:00:00Z");

    private final List<CustomsDeclarationRow> rows = new ArrayList<>();

    /**
     * マッパーを差し替える。
     *
     * <p><b>本物より甘くしない。</b> {@code search} は渡した行をそのまま返すだけで、
     * 督促の判定は一切しない——判定は読み口の責務であり、そこを検査している。</p>
     */
    private final CustomsDeclarationMapper declarations = new CustomsDeclarationMapper() {
        @Override
        public int insert(CustomsDeclarationRow row) {
            throw new UnsupportedOperationException("読み口の検査では書かない");
        }

        @Override
        public int updateStatus(CustomsStatusChange change) {
            throw new UnsupportedOperationException("読み口の検査では書かない");
        }

        @Override
        public CustomsDeclarationRow findByNumber(String declarationNumber) {
            return rows.stream()
                    .filter(row -> row.declarationNumber().equals(declarationNumber))
                    .findFirst().orElse(null);
        }

        @Override
        public List<CustomsDeclarationRow> findUnsettledByCargo(String trackingNumber) {
            return List.of();
        }

        @Override
        public CustomsDeclarationRow findLatestByCargo(String trackingNumber) {
            return null;
        }

        @Override
        public List<CustomsDeclarationRow> search(boolean includeCleared, String trackingNumber,
                String status, int limit) {
            return List.copyOf(rows);
        }

        @Override
        public List<CustomsDeclarationRow> findLatestByCargos(List<String> trackingNumbers) {
            return rows.stream()
                    .filter(row -> trackingNumbers.contains(row.trackingNumber()))
                    .toList();
        }

        @Override
        public List<CustomsDeclarationRow> findHeld() {
            return rows.stream().filter(row -> "HELD".equals(row.status())).toList();
        }
    };

    private final CustomsStatusHistoryMapper history = new CustomsStatusHistoryMapper() {
        @Override
        public int insert(CustomsStatusHistoryRow row) {
            throw new UnsupportedOperationException("読み口の検査では書かない");
        }

        @Override
        public List<CustomsStatusHistoryRow> findHistory(String declarationNumber) {
            return List.of();
        }
    };

    private final CustomsQueryHandler queries = new CustomsQueryHandler(declarations, history,
            Clock.fixed(NOW, ZoneId.of("Asia/Tokyo")));

    private void held(String number, Instant lastHeldAt) {
        rows.add(new CustomsDeclarationRow(number, "TRK-" + number, "b-1", "HELD",
                DECLARED, lastHeldAt, lastHeldAt, "検査待ち", "tracker01", NOW));
    }

    private List<CustomsDeclarationView> list(boolean overdueOnly) {
        return queries.handle(new FindCustomsDeclarationsQuery(true, null, null, overdueOnly))
                .items();
    }

    @Test
    @DisplayName("US29 §6: ちょうど 3 営業日は督促の対象ではない（「超えた」の境界）")
    void threeBusinessDaysIsNotOverdueYet() {
        held("IMP-3", HELD_3_DAYS);

        assertThat(list(false)).singleElement().satisfies(view -> {
            assertThat(view.heldBusinessDays())
                    .as("2026-09-30 → 2026-10-05 は土日を外して 3 営業日")
                    .isEqualTo(3);
            assertThat(view.overdue())
                    .as("閾値を >= に緩めるとここが赤になる")
                    .isFalse();
        });
    }

    @Test
    @DisplayName("US29 §6: 4 営業日は督促の対象（閾値を上げるとここが赤になる）")
    void fourBusinessDaysIsOverdue() {
        held("IMP-4", HELD_4_DAYS);

        assertThat(list(false)).singleElement().satisfies(view -> {
            assertThat(view.heldBusinessDays()).isEqualTo(4);
            assertThat(view.overdue()).isTrue();
        });
    }

    @Test
    @DisplayName("US29 §6: 一覧の絞り込みと件数が同じ判定を使う（判別できる形で見る）")
    void listAndCountUseTheSameJudgement() {
        held("IMP-3", HELD_3_DAYS);
        held("IMP-4", HELD_4_DAYS);

        // **督促でない留置を混ぜる。** 混ぜないと、件数側が全 HELD を数える
        // 実装に戻しても緑になる。
        assertThat(list(true))
                .extracting(CustomsDeclarationView::declarationNumber)
                .containsExactly("IMP-4");
        assertThat(queries.handle(new CountOverdueCustomsHoldsQuery()))
                .as("一覧に出るものだけを数える")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("留置から出たあとも営業日数が残り、督促の対象ではなくなる")
    void keepsDaysAfterSettlingButIsNotOverdue() {
        // 2026-09-29 に留置 → 2026-10-05 に通関済。営業日は 4 日。
        rows.add(new CustomsDeclarationRow("IMP-C", "TRK-C", "b-1", "CLEARED",
                DECLARED, NOW, HELD_4_DAYS, "証明書を受領", "tracker01", NOW));

        assertThat(list(false)).singleElement().satisfies(view -> {
            assertThat(view.heldBusinessDays())
                    .as("読むときに数えないと調整根拠が消える（列は持たない）")
                    .isEqualTo(4);
            assertThat(view.overdue())
                    .as("決着しているので督促の対象ではない")
                    .isFalse();
        });
    }

    @Test
    @DisplayName("留置したことが無ければ 0（数えるものが無い）")
    void neverHeldCountsZero() {
        rows.add(new CustomsDeclarationRow("IMP-P", "TRK-P", "b-1", "PENDING",
                DECLARED, DECLARED, null, null, null, NOW));

        assertThat(list(false)).singleElement().satisfies(view -> {
            assertThat(view.heldBusinessDays()).isZero();
            assertThat(view.overdue()).isFalse();
        });
    }

    @Test
    @DisplayName("留置の日付が未来でも一覧を落とさない（1 件の変な行で他が読めなくなるより良い）")
    void doesNotBreakTheWholeListOnFutureHold() {
        held("IMP-F", NOW.plusSeconds(86_400L * 3));

        assertThat(list(false)).singleElement().satisfies(view -> {
            assertThat(view.heldBusinessDays()).isZero();
            assertThat(view.overdue()).isFalse();
        });
    }

    @Test
    @DisplayName("督促の対象が先に来る（留置営業日の多い順）")
    void sortsByHeldBusinessDaysDescending() {
        held("IMP-3", HELD_3_DAYS);
        held("IMP-4", HELD_4_DAYS);

        assertThat(list(false))
                .extracting(CustomsDeclarationView::declarationNumber)
                .containsExactly("IMP-4", "IMP-3");
    }

    @Test
    @DisplayName("営業日で数える（暦日なら 3 営業日の申告が督促の対象になってしまう）")
    void countsBusinessDaysNotCalendarDays() {
        held("IMP-3", HELD_3_DAYS);

        // 2026-09-30 → 2026-10-05 は暦日で 5 日。営業日は 3 日。
        assertThat(LocalDate.of(2026, Month.SEPTEMBER, 30)
                .datesUntil(LocalDate.of(2026, Month.OCTOBER, 5)).count())
                .as("暦日は 5 日ある")
                .isEqualTo(5);
        assertThat(list(false)).singleElement()
                .extracting(CustomsDeclarationView::heldBusinessDays)
                .isEqualTo(3);
    }
}
