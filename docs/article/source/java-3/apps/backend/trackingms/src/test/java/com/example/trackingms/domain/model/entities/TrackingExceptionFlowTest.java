package com.example.trackingms.domain.model.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.ExceptionType;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 例外の起票と解決（US19・US20・[ADR-024] 決定 2）。
 *
 * <p>ここで確かめるのは<strong>発生前の状態が守られること</strong>である。集約が
 * 履歴から導いていると、ここは緑のまま本番だけが誤復帰する。
 */
@DisplayName("追跡の例外")
class TrackingExceptionFlowTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final LocalDate DEADLINE = LocalDate.of(2030, Month.SEPTEMBER, 20);
    private static final Instant NOW = Instant.parse("2027-09-03T00:00:00Z");

    /** 組み立てをラムダの外に出すための補助。中に置くと、どの呼び出しが投げたか分からない。 */
    private static TrackingActivity raiseDamage(TrackingActivity activity) {
        return activity.raiseException(ExceptionType.DAMAGE, "破損しています", NOW);
    }

    private static TrackingActivity onboard() {
        return TrackingActivity.start(TrackingNumber.of("TRK-20260823-0001"),
                        TrackingBookingId.of("BKG-2026000001"), TOKYO, LOS_ANGELES, DEADLINE)
                .afterHandling("RECEIVE", "JPTYO")
                .afterHandling("LOAD", "JPTYO");
    }

    @Nested
    @DisplayName("例外イベント単体")
    class EventValidation {

        @Test
        @DisplayName("例外種別・発生状況・発生日時が無ければ作れない")
        void requiresTheEssentialFields() {
            assertThatThrownBy(() -> TrackingExceptionEvent.raise(null, "遅延", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TrackingExceptionEvent.raise(ExceptionType.DELAY, " ", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TrackingExceptionEvent.raise(ExceptionType.DELAY, "遅延", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("対応内容が無ければ解決できず、二度解決できない")
        void requiresNotesAndRejectsDuplicateResolution() {
            TrackingExceptionEvent raised =
                    TrackingExceptionEvent.raise(ExceptionType.DAMAGE, "外装に破れ", NOW);

            assertThatThrownBy(() -> raised.resolve(" ", NOW))
                    .isInstanceOf(IllegalArgumentException.class);

            TrackingExceptionEvent resolved = raised.resolve("梱包し直しました", NOW);
            assertThat(resolved.unresolved()).isFalse();

            assertThatThrownBy(() -> resolved.resolve("再解決", NOW))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("起票するとき（US19-1・US20-1）")
    class WhenRaising {

        @Test
        @DisplayName("起票すると、状態が例外発生になる")
        void movesToException() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DELAY, "台風により出港が遅れています", NOW);

            assertThat(raised.trackingStatus()).isEqualTo(TrackingStatus.EXCEPTION);
            assertThat(raised.activeException()).isPresent();
        }

        /**
         * <strong>多重起票を許さない</strong>（[ADR-024] 決定 2）。
         *
         * <p>2 件目を許すと、発生前の状態が {@code EXCEPTION} で上書きされ、
         * <strong>解決しても戻れなくなる</strong>。
         */
        @Test
        @DisplayName("未解決の例外があるあいだは、2 件目を起票できない")
        void rejectsASecondException() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DELAY, "遅延しています", NOW);

            // 組み立てをラムダの外に出す。中に置くと、どの呼び出しが投げたのか分からない
            assertThatThrownBy(() -> raiseDamage(raised))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未解決の例外があります");
        }

        /** 緊急かどうかは種別が答える（[ADR-024] 決定 3）。対で確かめる。 */
        @Test
        @DisplayName("紛失だけが緊急になる")
        void onlyLostIsUrgent() {
            assertThat(onboard().raiseException(ExceptionType.LOST, "所在不明", NOW)
                    .hasUrgentException()).isTrue();
            assertThat(onboard().raiseException(ExceptionType.DAMAGE, "破損", NOW)
                    .hasUrgentException()).isFalse();
        }

        /**
         * <strong>手では起票できない種別を、集約でも断る。</strong>
         *
         * <p>入口の検査だけに頼ると、入口が増えた日に素通りする（返済枠 0.5 と同じ形）。
         */
        @Test
        @DisplayName("自動で検知する種別は、集約でも断る")
        void rejectsAutoDetectedTypes() {
            TrackingActivity subject = onboard();
            assertThatThrownBy(() ->
                    subject.raiseException(ExceptionType.MISROUTE, "誤配", NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("手では起票できません");
        }
    }

    /**
     * 仕組みが検知して起票する道（US28 の誤配・US29 の税関保留）。
     *
     * <p><strong>人の手番とは別の入口にする</strong>（IT9 返済枠 0.4）。同じ
     * {@code raiseException} に「これは仕組みからだ」という引数を足すと、呼び出し側の
     * どれが人でどれが仕組みかがコードから読めなくなり、手番の検査は 3 か所目・4 か所目へ
     * 増える。入口を分ければ、それぞれが自分の断り方だけを持つ。
     */
    @Nested
    @DisplayName("仕組みが検知したとき（US28・US29）")
    class WhenDetected {

        @Test
        @DisplayName("手では起票できない種別を、仕組みは起票できる")
        void detectsWhatOperatorsCannotRaise() {
            TrackingActivity subject = onboard();

            TrackingActivity detected =
                    subject.detectException(ExceptionType.CUSTOMS_HOLD, "税関で留置", NOW);

            assertThat(detected.trackingStatus()).isEqualTo(TrackingStatus.EXCEPTION);
            assertThat(detected.activeException()).isPresent();
            assertThat(detected.activeException().orElseThrow().exceptionType())
                    .isEqualTo(ExceptionType.CUSTOMS_HOLD);
        }

        /**
         * <strong>人が起票する種別を、仕組みの入口からは入れない。</strong>
         *
         * <p>入れられると、購読側の不具合で「遅延」が誰の判断も経ずに立つ。
         * 遅延・破損・紛失は人が見て決めることであり、仕組みが決めることではない。
         */
        @Test
        @DisplayName("人が起票する種別は、仕組みの入口から入れない")
        void refusesWhatOnlyOperatorsDecide() {
            TrackingActivity subject = onboard();

            assertThatThrownBy(() ->
                    subject.detectException(ExceptionType.DELAY, "遅延", NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("自動では起票できません");
        }

        /** 未解決の例外は 1 件までという規則は、仕組みからの起票にも効く。 */
        @Test
        @DisplayName("未解決の例外があるときは、仕組みからも起票しない")
        void doesNotStackOnAnUnresolvedException() {
            TrackingActivity subject = onboard().raiseException(ExceptionType.DELAY, "遅延", NOW);

            assertThatThrownBy(() ->
                    subject.detectException(ExceptionType.CUSTOMS_HOLD, "税関で留置", NOW))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("解決するとき（US19-4）")
    class WhenResolving {

        /**
         * <strong>発生前の状態に戻る。</strong>
         *
         * <p>受領待ちまで巻き戻らないことを<strong>対で見る</strong>——「戻る」だけを
         * 見ると、初期状態へ戻す実装でも緑になる。
         */
        @Test
        @DisplayName("解決すると、例外が起きる前の状態に戻る")
        void returnsToTheStatusBeforeTheException() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DELAY, "遅延しています", NOW);

            // 遅延の解決には新しい到着予定日が要る（返済枠 0.6）。ここで見るのは状態の戻り先
            TrackingActivity resolved = raised.resolveException(null, "別便に振り替えました", NOW,
                    LocalDate.of(2027, Month.SEPTEMBER, 20));

            assertThat(resolved.trackingStatus())
                    .as("発生前の状態に戻っていない")
                    .isEqualTo(TrackingStatus.LOADED);
            assertThat(resolved.trackingStatus())
                    .as("初期状態まで巻き戻っている")
                    .isNotEqualTo(TrackingStatus.NOT_RECEIVED);
            assertThat(resolved.activeException()).isEmpty();
        }

        /** US19-4。新しい到着予定日を受け取る。遅延以外は空なら据え置く。 */
        @Test
        @DisplayName("解決のときに、新しい到着予定日を受け取れる")
        void acceptsANewEstimatedArrival() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DELAY, "遅延しています", NOW);

            LocalDate newArrival = LocalDate.of(2027, Month.SEPTEMBER, 20);
            assertThat(raised.resolveException(null, "別便に振り替えました", NOW, newArrival)
                    .estimatedArrival()).contains(newArrival);
        }

        /**
         * <strong>遅延を解決するなら、いつ着くのかを言う</strong>（IT9 返済枠 0.6・
         * IT8 レビュー #18）。
         *
         * <p>遅延の解決とは「遅れの見通しが立った」ことである。到着予定日を入れずに
         * 閉じると、遅れる前の古い予定日が残り続け、荷主は過ぎた日付を見る。
         * それは「解決した」の意味ではない。
         *
         * <p>破損・紛失には求めない。到着の見込みとは別の話であり、求めると
         * 担当者は形だけの日付を入れて閉じる。
         */
        @Test
        @DisplayName("遅延の解決は、新しい到着予定日が無ければ断る")
        void requiresANewEstimateWhenResolvingADelay() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DELAY, "遅延しています", NOW);

            assertThatThrownBy(() -> raised.resolveException(null, "別便に振り替えました", NOW, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("到着予定日");
        }

        @Test
        @DisplayName("遅延以外の解決では、到着予定日を求めない")
        void doesNotRequireAnEstimateForOtherExceptions() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DAMAGE, "外装に破れ", NOW);

            assertThat(raised.resolveException(null, "梱包し直しました", NOW, null).estimatedArrival())
                    .as("空の指定で、もとの予定日を消している")
                    .isEqualTo(raised.estimatedArrival());
        }

        /**
         * <strong>開いていた一覧が古いまま閉じられない</strong>（IT9 返済枠 0.7・
         * IT8 レビュー #14）。
         *
         * <p>未解決の例外一覧を開いたまま席を外している間に、別の担当者が同じ例外を
         * 解決し、次の例外が起票されることがある。画面が持っている {@code exceptionId} は
         * そのとき古い。照合しないと、<strong>見ていたのとは違う例外を、見ていた
         * つもりの理由で閉じる</strong>ことになる。
         *
         * <p>受け取っているのに使っていなかった。引数があること自体が「照合している」
         * ように読めるため、気づきにくい形だった。
         */
        @Test
        @DisplayName("いま開いている例外と違う番号では、解決できない")
        void refusesToResolveAStaleException() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DAMAGE, "外装に破れ", NOW);
            Long current = raised.activeException().orElseThrow().id();

            assertThatThrownBy(() -> raised.resolveException(
                    current == null ? 999L : current + 1, "梱包し直しました", NOW, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("別の担当者");
        }

        @Test
        @DisplayName("未解決の例外が無ければ、解決できない")
        void rejectsResolvingWithoutAnException() {
            TrackingActivity subject = onboard();
            assertThatThrownBy(() -> subject.resolveException(null, "直しました", NOW, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未解決の例外がありません");
        }

        @Test
        @DisplayName("対応内容は必須")
        void requiresResolutionNotes() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DELAY, "遅延しています", NOW);

            assertThatThrownBy(() -> raised.resolveException(null, " ", NOW, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("例外が起きているあいだ")
    class WhileInException {

        /**
         * <strong>例外のあいだは、荷役でも手動でも動かない。</strong>
         *
         * <p>動かせると、解決したときの戻り先が変わってしまう。
         */
        @Test
        @DisplayName("荷役が届いても、状態は動かない")
        void doesNotAdvanceOnHandling() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DELAY, "遅延しています", NOW);

            assertThat(raised.afterHandling("UNLOAD", "USLAX")).isSameAs(raised);
        }

        @Test
        @DisplayName("手動更新も断る")
        void rejectsManualUpdate() {
            TrackingActivity raised = onboard()
                    .raiseException(ExceptionType.DELAY, "遅延しています", NOW);

            assertThatThrownBy(() ->
                    raised.updateManually(TrackingStatus.UNLOADED, LOS_ANGELES, NOW))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("手で更新するとき（US17-2）")
    class WhenUpdatingManually {

        @Test
        @DisplayName("進む向きには更新できる")
        void advances() {
            TrackingActivity updated =
                    onboard().updateManually(TrackingStatus.ONBOARD_CARRIER, TOKYO, NOW);

            assertThat(updated.trackingStatus()).isEqualTo(TrackingStatus.ONBOARD_CARRIER);
            assertThat(updated.currentLocation()).isEqualTo(TOKYO);
        }

        /**
         * <strong>戻る向きには更新できない</strong>（[ADR-024] 決定 1）。
         *
         * <p>荷主が見ているのは 1 本の状態であり、<strong>どの入口から動いたかは
         * 荷主に見えない</strong>。手動経路にだけ抜け道を作ると、IT7 で塞いだ巻き戻りが
         * 人の操作で起きる。
         */
        @Test
        @DisplayName("戻る向きには更新できない")
        void doesNotRegressOnManualUpdate() {
            TrackingActivity subject = onboard();
            assertThatThrownBy(() ->
                    subject.updateManually(TrackingStatus.NOT_RECEIVED, TOKYO, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("前の状態には戻せません");
        }

        /** 同じ状態への更新も進んでいない。「変えていない」ことと「進んだ」ことは違う。 */
        @Test
        @DisplayName("同じ状態への更新も断る")
        void rejectsUpdatingToTheSameStatus() {
            TrackingActivity subject = onboard();
            assertThatThrownBy(() -> subject.updateManually(TrackingStatus.LOADED, TOKYO, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * <strong>進行の道の外へは動かせない。</strong>
         *
         * <p>例外への出入りは専用の操作（起票・解決）だけが行う（返済枠 0.3）。
         */
        @Test
        @DisplayName("例外・不明へは手でも動かせない")
        void rejectsOffPathStatuses() {
            TrackingActivity subject = onboard();
            assertThatThrownBy(() -> subject.updateManually(TrackingStatus.EXCEPTION, TOKYO, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> subject.updateManually(TrackingStatus.UNKNOWN, TOKYO, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
