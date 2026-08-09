package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.tracking.domain.model.ExceptionOccurrence;
import com.example.cargotracker.tracking.domain.model.ExceptionResolution;
import com.example.cargotracker.tracking.domain.model.ExceptionType;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.TrackingDestination;
import com.example.cargotracker.tracking.domain.model.TrackingExceptionEvent;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.TransportStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 例外の発生と解決（US19 / US20）。
 *
 * <p><strong>本テストの中心は「解決したらどこへ戻るか」である。</strong>
 * 戻り先を荷役の履歴から導き直すと、ユニットテストは緑になりうるが、
 * 例外の発生中に荷役が入った瞬間に誤った状態へ戻る。
 * だから<strong>発生中に状態を進めてから解決する</strong>ケースを必ず置く。
 */
@DisplayName("例外の発生と解決（US19 / US20）")
class TrackingExceptionTest {

    private static final Instant 発生 = Instant.parse("2026-09-05T02:00:00Z");
    private static final Instant 現在 = Instant.parse("2026-09-10T00:00:00Z");
    private static final Location 上海 = Location.of("CNSHA");

    private static TrackingActivity 追跡を始める() {
        return TrackingActivity.issue(
                new TrackingNumber("TRK-20260901-0001"),
                new TrackingBookingId(UUID.randomUUID()),
                new TrackingDestination(
                        Location.of("USLAX"), LocalDate.of(2026, Month.SEPTEMBER, 20)));
    }

    /** 対応内容（対応方針だけ。新しい到着予定日は添えない）。 */
    private static ExceptionResolution 対応内容(String notes) {
        return ExceptionResolution.report(notes, null);
    }

    private static TrackingActivityEvent 荷役(TrackingEventType type, String at) {
        return TrackingActivityEvent.fromHandling(
                type, Instant.parse(at), 上海, null);
    }

    /** 受取済まで進んだ追跡。**例外は輸送の途中で起きる。** */
    private static TrackingActivity 輸送中の追跡() {
        TrackingActivity activity = 追跡を始める();
        activity.recordEvent(荷役(TrackingEventType.RECEIVE, "2026-09-01T02:00:00Z"));
        return activity;
    }

    /**
     * <strong>画面から登録できる種別は 3 つだけである</strong>（US19 / US20）。
     *
     * <p>種別を 1 つ足したときに、手動で起票してよいかの判断が漏れても
     * 気づけない形にしない。税関保留は ADR-006（外部システムとは連携しない）の下で
     * どう起票するかが未決であり、選べる形にすると
     * <strong>「選べるのに正しく使えない」項目が画面に残る</strong>。
     */
    @Test
    void 画面から登録できるのは遅延と破損と紛失だけである() {
        assertThat(ExceptionType.manuallyRaisable())
                .containsExactly(ExceptionType.DELAY, ExceptionType.DAMAGE, ExceptionType.LOST)
                .doesNotContain(ExceptionType.CUSTOMS_HOLD);
    }

    /** エスカレーションが要るのは紛失だけである。**種別が自分で持つ。** */
    @Test
    void エスカレーションが要るのは紛失だけである() {
        assertThat(java.util.Arrays.stream(ExceptionType.values())
                .filter(ExceptionType::escalationRequired)
                .toList())
                .containsExactly(ExceptionType.LOST);
    }

    @Nested
    @DisplayName("起票する")
    class 起票する {

        /** 受入基準（US19 / US20）: 種別・発生状況（場所・日時・理由）を記録できる。 */
        @Test
        void 場所と日時と理由を記録できる() {
            TrackingActivity activity = 輸送中の追跡();

            TrackingExceptionEvent raised = activity.raiseException(
                    new ExceptionOccurrence(ExceptionType.DELAY, 上海, 発生, "台風により出港が 3 日遅れています"), 現在);

            assertThat(raised.exceptionType()).isEqualTo(ExceptionType.DELAY);
            assertThat(raised.location()).isEqualTo(上海);
            assertThat(raised.occurredAt()).isEqualTo(発生);
            assertThat(raised.description()).contains("台風");
        }

        /** 受入基準（US19 / US20）: 記録後、貨物状態が「例外発生」に更新される。 */
        @Test
        void 起票すると輸送状態が例外になる() {
            TrackingActivity activity = 輸送中の追跡();

            activity.raiseException(
                    new ExceptionOccurrence(ExceptionType.DELAY, 上海, 発生, "遅延"), 現在);

            assertThat(activity.transportStatus()).isEqualTo(TransportStatus.EXCEPTION);
            assertThat(activity.hasActiveException()).isTrue();
        }

        /**
         * 受入基準（US20）: <strong>紛失は緊急フラグが設定される。</strong>
         *
         * <p>フラグの値は呼び出し側から渡さない。渡せる形にすると、
         * 紛失をエスカレーションせずに起票できてしまう。
         */
        @Test
        void 紛失はエスカレーションのフラグが立つ() {
            TrackingActivity activity = 輸送中の追跡();

            var raised = activity.raiseException(
                    new ExceptionOccurrence(ExceptionType.LOST, 上海, 発生, "紛失"), 現在);

            assertThat(raised.escalationFlag()).isTrue();
        }

        /**
         * <strong>遅延と破損ではフラグは立たない。</strong>
         *
         * <p>すべてを管理職に上げると、本当に上げるべき紛失が埋もれる。
         * 「立つこと」だけを確かめると、常に true を返す実装でも緑になる。
         */
        @Test
        void 遅延と破損ではエスカレーションのフラグは立たない() {
            assertThat(輸送中の追跡()
                    .raiseException(
                    new ExceptionOccurrence(ExceptionType.DELAY, 上海, 発生, "遅延"), 現在)
                    .escalationFlag()).isFalse();
            assertThat(輸送中の追跡()
                    .raiseException(
                    new ExceptionOccurrence(ExceptionType.DAMAGE, 上海, 発生, "破損"), 現在)
                    .escalationFlag()).isFalse();
        }

        /**
         * <strong>引取が完了した貨物には起票できない。</strong>
         *
         * <p>輸送が終わった貨物に遅延も紛失も起きない。塞がないと、解決したときに
         * 「引取完了」へ戻すという意味の通らない操作ができる。
         */
        @Test
        void 引取が完了した貨物には起票できない() {
            TrackingActivity activity = 輸送中の追跡();
            activity.recordEvent(荷役(TrackingEventType.CLAIM, "2026-09-08T02:00:00Z"));

            assertThatThrownBy(() -> assertThat(activity.raiseException(
                    new ExceptionOccurrence(ExceptionType.DELAY, 上海, 発生, "遅延"), 現在)).isNotNull())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("引取が完了した貨物には例外を登録できません");
        }

        /**
         * <strong>未解決の例外は同時に何件でも持てる</strong>（IT11 / C21）。
         *
         * <p>IT10 は「復帰先が 2 つになる」という<strong>実装上の理由</strong>で
         * 未解決を 1 件に限った。しかし遅延の対応中に破損が判明することは実務では
         * 珍しくなく、この作りでは破損を登録するために遅延を「解決」する必要があり、
         * その瞬間に荷主へ<strong>事実でない対応報告</strong>が飛ぶ。
         *
         * <p>復帰先は<strong>最初に起票された例外の発生前の状態に固定する</strong>。
         * 2 件目以降が「例外発生」を発生前の状態として持つことはない。
         */
        @Test
        void 未解決の例外を同時に複数持てる() {
            TrackingActivity activity = 輸送中の追跡();
            activity.raiseException(
                    new ExceptionOccurrence(ExceptionType.DELAY, 上海, 発生, "遅延"), 現在);

            var second = activity.raiseException(
                    new ExceptionOccurrence(ExceptionType.DAMAGE, 上海, 発生, "破損"), 現在);

            assertThat(activity.exceptions()).hasSize(2);
            assertThat(second.statusBefore())
                    .as("2 件目の復帰先は「例外発生」ではなく、最初の例外の発生前の状態である")
                    .isEqualTo(TransportStatus.RECEIVED);
        }

        /**
         * <strong>1 件解決しても、まだ未解決があるうちは例外発生のままにする</strong>（C21）。
         *
         * <p>破損が片づいても遅延が続いているなら、貨物はまだ例外の中にある。
         * ここで通常状態に戻すと、<strong>一覧から消えて誰も見なくなる</strong>。
         */
        @Test
        void 未解決が残っているうちは例外発生のままにする() {
            TrackingActivity activity = 二件の例外を抱えた追跡();

            activity.resolveException(
                    activity.exceptions().getFirst().id(), 対応内容("片方は解消"), 現在);

            assertThat(activity.transportStatus()).isEqualTo(TransportStatus.EXCEPTION);
        }

        /** <strong>すべて解決したら、最初の例外の発生前の状態へ戻る</strong>（C21）。 */
        @Test
        void すべて解決すると最初の例外の発生前の状態へ戻る() {
            TrackingActivity activity = 二件の例外を抱えた追跡();
            for (var e : List.copyOf(activity.exceptions())) {
                activity.resolveException(e.id(), 対応内容("対応済み"), 現在);
            }

            assertThat(activity.transportStatus()).isEqualTo(TransportStatus.RECEIVED);
        }

        /** <strong>未来に起きた例外は記録できない。</strong> */
        @Test
        void 未来の発生日時は受け付けない() {
            TrackingActivity activity = 輸送中の追跡();
            Instant future = 現在.plusSeconds(3600);

            assertThatThrownBy(() -> assertThat(activity.raiseException(
                    new ExceptionOccurrence(ExceptionType.DELAY, 上海, future, "遅延"), 現在)).isNotNull())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未来の日時は指定できません");
        }

        /**
         * 発生場所は必須である（受入基準「発生状況（**場所**・日時・理由）」）。
         *
         * <p><strong>検査は新規起票の入口（{@code raise}）が持つ。</strong>
         * 正準コンストラクタに置くと復元経路も通り、場所の列が無かったころに
         * 起票された例外を読み戻せなくなる（次のテストが対である）。
         */
        @Test
        void 発生場所を欠いた起票は受け付けない() {
            assertThatThrownBy(() -> assertThat(ExceptionOccurrence.raise(
                    ExceptionType.DELAY, null, 発生, "遅延")).isNotNull())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("発生場所は必須です");
        }

        /**
         * <strong>発生場所の無い例外も読み戻せる。</strong>
         *
         * <p>{@code location_unlocode} は V22 で足した列であり、それ以前に起票された
         * 例外は場所を持ちようがない。復元で拒むと<strong>その貨物の集約ごと
         * 読めなくなり、画面が 500 になる</strong>。
         * <strong>守る時点が違うのであって、守りが緩いのではない。</strong>
         */
        @Test
        void 発生場所の無い例外も読み戻せる() {
            var restored = TrackingExceptionEvent.reconstruct(
                    1L,
                    new ExceptionOccurrence(ExceptionType.DELAY, null, 発生, "旧形式"),
                    false, TransportStatus.RECEIVED, null, null);

            assertThat(restored.location()).isNull();
            assertThat(restored.exceptionType()).isEqualTo(ExceptionType.DELAY);
        }
    }

    @Nested
    @DisplayName("解決する")
    class 解決する {

        /** 受入基準（US19）: 対応内容を入力して解決できる。 */
        @Test
        void 対応内容を記録して解決できる() {
            TrackingActivity activity = 例外を起票した追跡(ExceptionType.DELAY);
            var raised = activity.exceptions().getFirst();

            var resolved = activity.resolveException(raised.id(), 対応内容("代替便に振り替えました"), 現在);

            assertThat(resolved.isResolved()).isTrue();
            assertThat(resolved.resolvedAt()).isEqualTo(現在);
            assertThat(resolved.resolutionNotes()).contains("代替便");
            assertThat(activity.hasActiveException()).isFalse();
        }

        /**
         * <strong>解決すると発生前の状態に戻る。</strong>
         *
         * <p>受取済で起きた例外を解決したら受取済に戻る。「未受取」でも
         * 「例外」でもない。
         */
        @Test
        void 解決すると発生前の状態に戻る() {
            TrackingActivity activity = 例外を起票した追跡(ExceptionType.DELAY);
            var raised = activity.exceptions().getFirst();
            assertThat(raised.statusBefore()).isEqualTo(TransportStatus.RECEIVED);

            activity.resolveException(raised.id(), 対応内容("対応済み"), 現在);

            assertThat(activity.transportStatus()).isEqualTo(TransportStatus.RECEIVED);
        }

        /**
         * <strong>例外の発生中に進めた状態には戻さない</strong>（本 IT の最重要）。
         *
         * <p>荷役の履歴から復帰先を導くと、ここで「積み込み済」に戻る。
         * <strong>それは例外が起きる前の状態ではない。</strong>
         * 発生前の状態を永続化しているからこそ、正しく「受取済」へ戻せる。
         */
        @Test
        void 例外の発生中に進めた状態には戻さない() {
            TrackingActivity activity = 例外を起票した追跡(ExceptionType.DELAY);
            var raised = activity.exceptions().getFirst();
            // 例外の対応中に現場が積込を記録した（実務では起こりうる）
            activity.recordEvent(荷役(TrackingEventType.LOAD, "2026-09-06T02:00:00Z"));
            assertThat(activity.transportStatus()).isEqualTo(TransportStatus.LOADED);

            activity.resolveException(raised.id(), 対応内容("対応済み"), 現在);

            assertThat(activity.transportStatus())
                    .as("戻る先は例外の発生前（受取済）であり、発生中に進めた積み込み済ではない")
                    .isEqualTo(TransportStatus.RECEIVED);
        }

        /** <strong>二度は解決できない。</strong> 最初の対応日時が上書きされてしまう。 */
        @Test
        void 解決済みの例外は再解決できない() {
            TrackingActivity activity = 例外を起票した追跡(ExceptionType.DELAY);
            var raised = activity.exceptions().getFirst();
            activity.resolveException(raised.id(), 対応内容("対応済み"), 現在);
            Instant later = 現在.plusSeconds(3600);

            assertThatThrownBy(() -> assertThat(
                    activity.resolveException(raised.id(), 対応内容("やり直し"), later)).isNotNull())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("すでに");
            assertThat(activity.exceptions().getFirst().resolvedAt()).isEqualTo(現在);
        }

        /** 存在しない例外は解決できない。 */
        @Test
        void 無い例外は解決できない() {
            TrackingActivity activity = 輸送中の追跡();

            assertThatThrownBy(() -> assertThat(
                    activity.resolveException(999L, 対応内容("対応済み"), 現在)).isNotNull())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("該当する例外がありません");
        }

        /**
         * 解決したあとは新しい例外を起票できる。
         *
         * <p>「未解決が 2 つ並ばない」は<strong>永久に 1 件だけ</strong>という意味ではない。
         * 遅延を解決したあとに破損が起きることは実務では普通にある。
         */
        @Test
        void 解決したあとは次の例外を起票できる() {
            TrackingActivity activity = 例外を起票した追跡(ExceptionType.DELAY);
            activity.resolveException(activity.exceptions().getFirst().id(), 対応内容("対応済み"), 現在);

            activity.raiseException(
                    new ExceptionOccurrence(ExceptionType.DAMAGE, 上海, 現在, "破損"), 現在);

            assertThat(activity.transportStatus()).isEqualTo(TransportStatus.EXCEPTION);
            assertThat(activity.exceptions()).hasSize(2);
        }
    }

    /**
     * 起票し、<strong>永続化を経たのと同じ形</strong>（ID の付いた例外）に読み戻す。
     *
     * <p>起票直後の例外は ID を持たない（採番はリポジトリの仕事である）。
     * 解決は ID で指すため、ID の無い例外を解決するテストは
     * <strong>本番では通らない経路を確かめてしまう</strong>。
     */
    private static TrackingActivity 例外を起票した追跡(ExceptionType type) {
        TrackingActivity activity = 輸送中の追跡();
        activity.raiseException(new ExceptionOccurrence(type, 上海, 発生, "対応中"), 現在);
        return 読み戻す(activity);
    }

    /**
     * 遅延と破損を同時に抱えた追跡（C21）。
     *
     * <p><strong>ID を採番してから解決する。</strong> 未保存の例外は ID がどれも 0 で、
     * ID で指す解決が 1 件目に当たってしまう。
     * <strong>本番では通らない経路を確かめないための土台である。</strong>
     */
    private static TrackingActivity 二件の例外を抱えた追跡() {
        TrackingActivity activity = 輸送中の追跡();
        activity.raiseException(
                new ExceptionOccurrence(ExceptionType.DELAY, 上海, 発生, "遅延"), 現在);
        activity.raiseException(
                new ExceptionOccurrence(
                        ExceptionType.DAMAGE, 上海, 発生.plusSeconds(3600), "破損"), 現在);
        return 読み戻す(activity);
    }

    private static TrackingActivity 読み戻す(TrackingActivity activity) {
        List<TrackingExceptionEvent> stored = new java.util.ArrayList<>();
        long id = 0;
        for (TrackingExceptionEvent e : activity.exceptions()) {
            id++;
            stored.add(TrackingExceptionEvent.reconstruct(
                    e.id() == 0 ? id : e.id(), e.occurrence(), e.escalationFlag(),
                    e.statusBefore(), e.resolvedAt(), e.resolution()));
        }
        return TrackingActivity.reconstruct(
                activity.trackingNumber(), activity.bookingId(), activity.transportStatus(),
                activity.events(), stored, activity.version(), activity.destination());
    }
}
