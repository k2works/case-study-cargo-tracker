package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.application.internal.outboundservices.acl
        .ApprovedCancellations;
import com.example.cargotracker.handling.application.internal.queryservices
        .DischargeOrderSelection;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 荷降し手配に<strong>どれが残るか</strong>という規則（IT17 の R3）。
 *
 * <p>この規則は IT16 まで {@code MyBatisHandlingQueryService} の中にあった。
 * <strong>業務の規則が、SQL を書くクラスの中にしか無かった</strong>。
 * 確かめるには実 PostgreSQL を起動し、承認と荷降しをデータで作る必要があり、
 * <strong>規則そのものを直接壊すテストが書けなかった</strong>。
 *
 * <p><strong>データの取り方は infrastructure に残す。</strong> CQRS の読み取り側を
 * MyBatis で書くのは本プロジェクトの設計であり（{@code architecture_backend.md}）、
 * 1 つだけ例外を作ると次に書く人がどちらに従えばよいか分からなくなる。
 * <strong>動かしたのは「規則」だけで、「問い合わせ」は動かしていない。</strong>
 */
@DisplayName("荷降し手配に残るもの（R3）")
class DischargeOrderSelectionTest {

    private static final UUID 大阪行き = UUID.randomUUID();
    private static final UUID ロサンゼルス行き = UUID.randomUUID();

    private static ApprovedCancellations.DischargeOrder 手配(UUID bookingId, int hoursAgo) {
        return new ApprovedCancellations.DischargeOrder(
                bookingId.toString(),
                "TRK-2026-%04d".formatted(hoursAgo),
                "JPOSA",
                "大阪",
                Instant.parse("2026-04-20T00:00:00Z").plusSeconds(hoursAgo * 3600L),
                "GENERAL");
    }

    /** <strong>降ろし済みは手配から落ちる。</strong> 済んだ仕事を待ち行列に残さない。 */
    @Test
    void 降ろし済みの予約は手配に残らない() {
        List<ApprovedCancellations.DischargeOrder> pending = DischargeOrderSelection.pending(
                List.of(手配(大阪行き, 1), 手配(ロサンゼルス行き, 2)),
                Set.of(大阪行き));

        assertThat(pending)
                .extracting(ApprovedCancellations.DischargeOrder::bookingId)
                .containsExactly(ロサンゼルス行き.toString());
    }

    /**
     * <strong>まだ降ろしていないものは残る。</strong>
     *
     * <p>常に空を返す実装でも上のテストは緑になる — <strong>落とすことと
     * 残すことの両方を見る。</strong>
     */
    @Test
    void 降ろしていない予約は手配に残る() {
        List<ApprovedCancellations.DischargeOrder> pending = DischargeOrderSelection.pending(
                List.of(手配(大阪行き, 1), 手配(ロサンゼルス行き, 2)),
                Set.of());

        assertThat(pending).hasSize(2);
    }

    /**
     * <strong>承認の古い順に並ぶ。</strong>
     *
     * <p>入力の順序をそのまま保つ。並べ替えは ACL ポートの SQL が
     * {@code ORDER BY decided_at} で行っており、<strong>ここで並べ直すと
     * 順序の規則が 2 か所に散る</strong>。
     */
    @Test
    void 受け取った順序を保つ() {
        var older = 手配(大阪行き, 1);
        var newer = 手配(ロサンゼルス行き, 5);

        assertThat(DischargeOrderSelection.pending(List.of(older, newer), Set.of()))
                .containsExactly(older, newer);
    }

    /**
     * <strong>同じ予約に手配が 2 つ来たら 1 つにまとめる。</strong>
     *
     * <p>作業員から見れば「その貨物を降ろす」という 1 つの仕事である。
     * 2 行出ると、片方を済ませたときにもう片方が残って見える。
     */
    @Test
    void 同じ予約の手配は一つにまとめる() {
        assertThat(DischargeOrderSelection.pending(
                        List.of(手配(大阪行き, 1), 手配(大阪行き, 3)), Set.of()))
                .hasSize(1);
    }

    /** <strong>手配が無ければ何も返さない。</strong> */
    @Test
    void 手配が無ければ空である() {
        assertThat(DischargeOrderSelection.pending(List.of(), Set.of())).isEmpty();
    }

    /**
     * <strong>予約 ID が読めない手配は落とす。</strong>
     *
     * <p>解析できない ID を例外にすると、<strong>1 件の壊れた行で一覧全体が
     * 開けなくなる</strong>。荷役作業員の手が止まるほうが重い。
     */
    @Test
    void 予約IDが読めない手配は落とす() {
        var broken = new ApprovedCancellations.DischargeOrder(
                "not-a-uuid", "TRK-2026-9999", "JPOSA", "大阪",
                Instant.parse("2026-04-20T00:00:00Z"), "GENERAL");

        assertThat(DischargeOrderSelection.pending(List.of(broken, 手配(大阪行き, 1)), Set.of()))
                .hasSize(1);
    }

    /** <strong>絞り込みに要る予約 ID を、同じ規則から取り出せる。</strong> */
    @Test
    void 問い合わせる予約IDを取り出せる() {
        assertThat(DischargeOrderSelection.bookingIds(
                        List.of(手配(大阪行き, 1), 手配(大阪行き, 3), 手配(ロサンゼルス行き, 2))))
                .containsExactly(大阪行き, ロサンゼルス行き);
    }
}
