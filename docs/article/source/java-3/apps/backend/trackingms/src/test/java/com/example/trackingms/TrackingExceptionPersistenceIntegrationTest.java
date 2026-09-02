package com.example.trackingms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.trackingms.application.internal.commandservices.ManageTrackingUseCase;
import com.example.trackingms.application.internal.commandservices.StartTrackingUseCase;
import com.example.trackingms.domain.repository.TrackingActivityRepository;
import com.example.trackingms.domain.repository.TrackingNoticeRepository;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 例外の起票と解決を、実 DB で確かめる（US19・US20・[ADR-024] 決定 2）。
 *
 * <p>ここで確かめるのは<strong>発生前の状態が行に残っていること</strong>である。
 * 集約の単体テストは、集約を持ち回るかぎり緑になる——履歴から導いていても、
 * 1 リクエストの中では履歴が手元にあるからである。
 *
 * <p><strong>保存して読み直してから解決する。</strong>これが唯一、再導出と本物の
 * 永続化を見分ける形である。
 */
@DisplayName("追跡の例外の永続化")
class TrackingExceptionPersistenceIntegrationTest extends TrackingIntegrationTestBase {

    /**
     * <strong>テストごとに別の貨物を使う。</strong>
     *
     * <p>DB はテスト間で共有される。同じ番号を使うと、前のテストが残した未解決の例外で
     * <strong>原因でないテストが後から落ちる</strong>（IT8 返済枠 0.8 と同じ形）。
     */
    private final java.util.concurrent.atomic.AtomicReference<String> number =
            new java.util.concurrent.atomic.AtomicReference<>();

    @Autowired
    private StartTrackingUseCase startTracking;

    @Autowired
    private ManageTrackingUseCase manage;

    @Autowired
    private TrackingActivityRepository activities;

    @Autowired
    private TrackingNoticeRepository notices;

    @BeforeEach
    void startTracking(org.junit.jupiter.api.TestInfo testInfo) {
        // メソッド名から番号を作る。実行順に依存しない
        number.set("TRK-20270903-%04d".formatted(
                Math.abs(testInfo.getTestMethod().orElseThrow().getName().hashCode() % 10000)));
        startTracking.start(number.get(), "BKG-2027000001", "JPTYO", "USLAX",
                LocalDate.of(2027, Month.OCTOBER, 20), null);
    }

    private String number() {
        return number.get();
    }

    private TrackingActivity reload() {
        return activities.findByTrackingNumber(TrackingNumber.of(number())).orElseThrow();
    }

    /**
     * <strong>発生前の状態が、リクエストをまたいで守られる</strong>（[ADR-024] 決定 2）。
     *
     * <p>保存して読み直してから解決する。集約を持ち回すと、履歴から再導出する実装でも
     * 緑になり、<strong>行に残っていないことに気づけない</strong>。
     */
    @Test
    @DisplayName("保存して読み直しても、例外の発生前の状態に戻る")
    void restoresTheStatusBeforeTheExceptionAcrossRequests() {
        manage.updateStatus(number(), "RECEIVED", "JPTYO", Instant.parse("2026-08-01T00:00:00Z"));
        manage.updateStatus(number(), "LOADED", "JPTYO", Instant.parse("2026-08-02T00:00:00Z"));
        manage.raiseException(number(), "DELAY", "台風により出港が遅れています");

        // **読み直す。**ここが再導出と本物の永続化を見分ける唯一の形である
        assertThat(reload().trackingStatus()).isEqualTo(TrackingStatus.EXCEPTION);
        assertThat(reload().statusBefore()).contains(TrackingStatus.LOADED);

        // 遅延の解決には新しい到着予定日が要る（返済枠 0.6）。ここで見るのは状態の戻り先
        manage.resolveException(number(), null, "別便に振り替えました",
                LocalDate.of(2027, Month.SEPTEMBER, 25));

        assertThat(reload().trackingStatus())
                .as("発生前の状態に戻っていない")
                .isEqualTo(TrackingStatus.LOADED);
        assertThat(reload().trackingStatus())
                .as("初期状態まで巻き戻っている")
                .isNotEqualTo(TrackingStatus.NOT_RECEIVED);
        assertThat(reload().activeException()).isEmpty();
    }

    /** US19-4。新しい到着予定日を受け取り、行に残す。 */
    @Test
    @DisplayName("解決のときの新しい到着予定日が、行に残る")
    void persistsTheNewEstimatedArrival() {
        manage.raiseException(number(), "DELAY", "遅延しています");

        manage.resolveException(number(), null, "別便に振り替えました",
                LocalDate.of(2027, Month.SEPTEMBER, 25));

        assertThat(reload().estimatedArrival())
                .contains(LocalDate.of(2027, Month.SEPTEMBER, 25));
    }

    /**
     * <strong>読み戻しで全項目が戻る。</strong>
     *
     * <p>項目を 1 つずつ比べる形にすると、属性が増えたときに比較を足し忘れ、
     * 保存できていない項目に気づけない。
     */
    @Test
    @DisplayName("起票した例外が、そのまま読み戻せる")
    void persistsEveryFieldOfTheException() {
        manage.raiseException(number(), "LOST", "積替港で所在が確認できません");

        assertThat(reload().activeException()).hasValueSatisfying(exception -> {
            assertThat(exception.exceptionType().name()).isEqualTo("LOST");
            assertThat(exception.description()).isEqualTo("積替港で所在が確認できません");
            assertThat(exception.occurredAt()).isNotNull();
            assertThat(exception.urgent()).as("紛失なのに緊急でない").isTrue();
            assertThat(exception.id()).as("採番されていない").isNotNull();
        });
    }

    /** [ADR-024] 決定 2。多重起票を DB 越しでも断る。 */
    @Test
    @DisplayName("未解決の例外があるあいだは、2 件目を起票できない")
    void rejectsASecondExceptionAcrossRequests() {
        manage.raiseException(number(), "DELAY", "遅延しています");

        String target = number();
        assertThatThrownBy(() -> manage.raiseException(target, "DAMAGE", "破損しています"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * US17-3・US18-3。<strong>状態が動いたら、経過にも残る</strong>。
     *
     * <p>状態は動いたのに経過に出ない行ができると、荷主は「いつ変わったか」を読めない。
     */
    @Test
    @DisplayName("手動更新と例外が、経過に積まれる")
    void appendsEventsForManualUpdatesAndExceptions() {
        manage.updateStatus(number(), "RECEIVED", "JPTYO", Instant.parse("2026-08-01T00:00:00Z"));
        manage.raiseException(number(), "DELAY", "遅延しています");

        // **起きた順に並ぶ。**手動更新は業務上の日時を運び、起票は「いま気づいた」時刻を持つ
        assertThat(activities.findEvents(TrackingNumber.of(number()), 100))
                .as("状態は動いたのに、経過に出ていない")
                .hasSize(2)
                .extracting(event -> event.source().name())
                .containsExactly("MANUAL", "EXCEPTION");
    }

    /**
     * [ADR-024] 決定 9。<strong>メールは送らず、通知した事実を残す</strong>。
     *
     * <p>文言に社内の手がかりを書かない——認証の外にある画面へ出る。
     */
    @Test
    @DisplayName("通知は送らず、荷主に見せる文言だけを記録する")
    void recordsNoticesWithoutSending() {
        manage.raiseException(number(), "LOST", "積替港で所在が確認できません");

        assertThat(notices.findByTrackingNumber(TrackingNumber.of(number()), 10))
                .hasSize(1)
                .allSatisfy(notice -> {
                    assertThat(notice.message()).contains("問題が発生しました");
                    // **種別を書かない。**上の欄で「問題が起きています」としか書かないのに
                    // お知らせで「紛失」と書けば、隠した意味が無い（[ADR-024] 決定 5）。
                    // とくに「紛失」は補償の話に直結する言葉である
                    assertThat(notice.message())
                            .as("例外の種別が荷主向けの文言に入っている")
                            .doesNotContain("紛失");
                    assertThat(notice.message())
                            .as("社内の手がかりが荷主向けの文言に入っている")
                            .doesNotContain("積替港")
                            .doesNotContain("BKG-");
                });
    }

    /** 現在地は行に残る。公開照会がこれを出す。 */
    @Test
    @DisplayName("手動更新した現在地が、行に残る")
    void persistsTheCurrentLocation() {
        manage.updateStatus(number(), "RECEIVED", "CNSHA", Instant.parse("2026-08-01T00:00:00Z"));

        assertThat(reload().currentLocation().name()).isEqualTo("Shanghai");
    }
}
