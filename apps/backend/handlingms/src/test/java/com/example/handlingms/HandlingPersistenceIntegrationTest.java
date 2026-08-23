package com.example.handlingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.handlingms.application.internal.RegisterHandlingActivityCommand;
import com.example.handlingms.application.internal.RegisterHandlingActivityUseCase;
import com.example.handlingms.application.port.CargoSnapshotFinder;
import com.example.handlingms.application.port.HandlingActivityRegistered;
import com.example.handlingms.application.port.HandlingActivityRepository;
import com.example.handlingms.application.port.HandlingEventNotifier;
import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.CargoSnapshot;
import com.example.handlingms.domain.model.HandlingActivity;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.model.LegSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 荷役の記録が実際の DB で成立することを確認する。
 *
 * <p>読み戻しで<strong>全項目が戻る</strong>ことを見る。項目ごとに比べる形にすると、
 * 属性が増えたときに比較を足し忘れ、保存できていない項目に気づけない（IT6 の欠陥 5）。
 */
@SpringBootTest
@Testcontainers
@Import({HandlingPersistenceIntegrationTest.StubbedCargoes.class,
    HandlingPersistenceIntegrationTest.RecordingNotifier.class})
@ActiveProfiles("integration")
@DisplayName("荷役の記録の永続化")
class HandlingPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 貨物の写しは ACL の向こう側にある。ここでは実物の代わりに固定の写しを返す。 */
    @TestConfiguration
    static class StubbedCargoes {

        @Bean
        @Primary
        CargoSnapshotFinder stubbedCargoSnapshotFinder() {
            return trackingNumber -> "TRK-20260823-0001".equals(trackingNumber.value())
                    ? Optional.of(CargoSnapshot.of("BKG-2026000001", "JPTYO", "USLAX",
                            List.of(new LegSnapshot("V0100", "JPTYO", "CNSHA"),
                                    new LegSnapshot("V0200", "CNSHA", "USLAX"))))
                    : Optional.empty();
        }
    }

    /** 発行の呼び出しを捕まえる差し替え。ここで見たいのは<strong>いつ呼ばれるか</strong>。 */
    @TestConfiguration
    static class RecordingNotifier {

        @Bean
        @Primary
        HandlingEventNotifier recordingHandlingEventNotifier() {
            return event -> {
                PUBLISHED.add(event);
                transactionActiveWhenPublished =
                        TransactionSynchronizationManager.isSynchronizationActive();
            };
        }
    }

    private static final List<HandlingActivityRegistered> PUBLISHED = new ArrayList<>();

    /** 発行の時点でトランザクションが生きていたか（[ADR-022] 決定 6）。 */
    private static boolean transactionActiveWhenPublished;

    @Autowired
    private RegisterHandlingActivityUseCase registerActivity;

    @Autowired
    private HandlingActivityRepository activities;

    private static RegisterHandlingActivityCommand claimCommand() {
        return new RegisterHandlingActivityCommand("TRK-20260823-0001", "CLAIM", "USLAX",
                Instant.parse("2026-08-23T02:00:00Z"), "handler01", null, "山田太郎（受取担当）");
    }

    /**
     * <strong>読み戻しで全項目が戻る。</strong>
     *
     * <p>集約ごと比べる。項目を 1 つずつ比べる形にすると、属性が増えたときに比較を
     * 足し忘れ、保存できていない項目に気づけない。
     */
    @org.junit.jupiter.api.BeforeEach
    void clearPublished() {
        // 静的に持つため、テストの順序で混ざらないよう毎回空にする
        PUBLISHED.clear();
        transactionActiveWhenPublished = false;
    }

    @Test
    @DisplayName("記録した内容が、そのまま読み戻せる")
    void persistsEveryField() {
        HandlingActivity registered = registerActivity.register(claimCommand()).orElseThrow();

        List<HandlingActivity> history =
                activities.findByBookingId(CargoBookingId.of("BKG-2026000001"), 100);

        assertThat(history)
                .as("記録した荷役が読み戻せない")
                .usingRecursiveFieldByFieldElementComparator()
                .contains(registered);
    }

    @Test
    @DisplayName("荷受人の確認と航海番号が、正しい作業にだけ入る")
    void persistsTypeSpecificFields() {
        HandlingActivity claimed = registerActivity.register(claimCommand()).orElseThrow();
        HandlingActivity loaded = registerActivity.register(new RegisterHandlingActivityCommand(
                "TRK-20260823-0001", "LOAD", "JPTYO", Instant.parse("2026-08-23T01:00:00Z"),
                "handler01", "V0100", null)).orElseThrow();

        assertThat(claimed.consigneeConfirmation()).isPresent();
        assertThat(claimed.voyageNumber()).isEmpty();
        assertThat(loaded.voyageNumber()).isPresent();
        assertThat(loaded.consigneeConfirmation()).isEmpty();
    }

    /** [ADR-023] 決定 3。予定外だったことが行に残る。 */
    @Test
    @DisplayName("予定外の作業は、予定外として保存される")
    void persistsOffRoute() {
        HandlingActivity offRoute = registerActivity.register(new RegisterHandlingActivityCommand(
                "TRK-20260823-0001", "UNLOAD", "SGSIN", Instant.parse("2026-08-23T03:00:00Z"),
                "handler01", "V0100", null)).orElseThrow();

        assertThat(offRoute.offRoute())
                .as("予定外だったことが記録に残っていない。US28 で判定し直すことになる")
                .isTrue();

        // **イベントにも載る。**受け手（US28・IT10）はこれを誤配検知の入力にする。
        // 保存だけを見ていると、イベント側で潰しても緑のままになる
        assertThat(PUBLISHED.getLast().offRoute())
                .as("予定外だったことがイベントに載っていない")
                .isTrue();
    }

    /** 荷役は起きた順に読むもの。新しい順にすると「受領の前に積込がある」ように見える。 */
    @Test
    @DisplayName("履歴は古い順に並ぶ")
    void ordersHistoryChronologically() {
        registerActivity.register(new RegisterHandlingActivityCommand("TRK-20260823-0001",
                "LOAD", "JPTYO", Instant.parse("2026-08-24T01:00:00Z"), "handler01", "V0100",
                null));
        registerActivity.register(new RegisterHandlingActivityCommand("TRK-20260823-0001",
                "RECEIVE", "JPTYO", Instant.parse("2026-08-24T00:00:00Z"), "handler01", null,
                null));

        List<HandlingActivity> history =
                activities.findByBookingId(CargoBookingId.of("BKG-2026000001"), 100);

        assertThat(history)
                .extracting(HandlingActivity::completionTime)
                .isSorted();
    }

    /**
     * <strong>発行の時点でトランザクションが生きている</strong>（[ADR-022] 決定 6）。
     *
     * <p>境目が無ければ「コミット後に発行する」機構は素通りする。IT6 では境目が
     * リポジトリの save にしか無く、機構が本番で一度も働いていなかった。
     */
    @Test
    @DisplayName("発行は、トランザクションの中から伝える")
    void publishesInsideTheTransaction() {
        registerActivity.register(claimCommand());

        assertThat(transactionActiveWhenPublished)
                .as("発行の時点でトランザクションが張られていない。"
                        + "コミット後に送る仕組みが素通りする")
                .isTrue();
        assertThat(PUBLISHED).hasSize(1);
        assertThat(PUBLISHED.getFirst().type()).isEqualTo("CLAIM");
    }

    @Test
    @DisplayName("知らない追跡番号では記録しない")
    void doesNotRegisterForUnknownTrackingNumber() {
        Optional<HandlingActivity> registered =
                registerActivity.register(new RegisterHandlingActivityCommand("TRK-99999999-9999",
                        "RECEIVE", "JPTYO", Instant.parse("2026-08-23T02:00:00Z"), "handler01",
                        null, null));

        assertThat(registered).isEmpty();
    }

    @Test
    @DisplayName("地点マスタに無い作業場所は断る")
    void rejectsUnknownLocation() {
        // 組み立てをラムダの外に出す。中に置くと、どの呼び出しが投げたのか分からない
        RegisterHandlingActivityCommand unknownLocation = new RegisterHandlingActivityCommand(
                "TRK-20260823-0001", "RECEIVE", "XXXXX",
                Instant.parse("2026-08-23T02:00:00Z"), "handler01", null, null);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> registerActivity.register(unknownLocation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 種別の要件は集約が守る。ここで見るのは、実際の経路でも守られること。 */
    @Test
    @DisplayName("荷受人の確認がない引取は、実 DB の経路でも断られる")
    void rejectsClaimWithoutConfirmation() {
        RegisterHandlingActivityCommand withoutConfirmation = new RegisterHandlingActivityCommand(
                "TRK-20260823-0001", "CLAIM", "USLAX",
                Instant.parse("2026-08-23T02:00:00Z"), "handler01", null, null);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> registerActivity.register(withoutConfirmation))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(HandlingType.CLAIM.requiresConsigneeConfirmation()).isTrue();
    }
}
