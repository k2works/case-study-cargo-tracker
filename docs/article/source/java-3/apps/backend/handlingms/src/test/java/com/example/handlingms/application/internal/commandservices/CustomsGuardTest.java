package com.example.handlingms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.handlingms.application.internal.outboundservices.acl.CargoSnapshotFinder;
import com.example.handlingms.domain.repository.CustomsDeclarationRepository;
import com.example.handlingms.domain.repository.HandlingActivityRepository;
import com.example.handlingms.application.internal.outboundservices.acl.HandlingEventNotifier;
import com.example.handlingms.domain.repository.LocationRepository;
import com.example.handlingms.domain.model.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.model.valueobjects.CargoBookingId;
import com.example.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.handlingms.domain.model.aggregates.CustomsDeclaration;
import com.example.handlingms.domain.model.valueobjects.CustomsStatus;
import com.example.handlingms.domain.model.valueobjects.DeclarationNumber;
import com.example.handlingms.domain.model.aggregates.HandlingActivity;
import com.example.handlingms.domain.model.valueobjects.HandlingTrackingNumber;
import com.example.handlingms.domain.model.valueobjects.HandlingType;
import com.example.shared.domain.model.Location;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 引取（CLAIM）の通関ガード（US29-3・[ADR-023] 決定 4 の拡張点）。
 *
 * <p><strong>通関が下りていない貨物を引き渡すと、税関との関係で会社が責任を負う。</strong>
 *
 * <p><strong>名簿方式は未登録を素通りさせない</strong>（過去 take の教訓）。申告が
 * 1 件も無い貨物は「通関済でない」であって「検査の対象外」ではない。
 */
@DisplayName("引取の通関ガード")
class CustomsGuardTest {

    private static final String TRACKING = "TRK-20260823-0001";
    private static final String BOOKING = "BKG-2026000001";
    private static final Instant NOW = Instant.parse("2027-09-10T00:00:00Z");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private final List<CustomsDeclaration> declarationsStored = new ArrayList<>();
    private final List<HandlingActivity> registered = new ArrayList<>();

    private final CargoSnapshotFinder cargoes = trackingNumber ->
            TRACKING.equals(trackingNumber.value())
                    ? Optional.of(CargoSnapshot.of(BOOKING, "JPTYO", "USLAX", List.of()))
                    : Optional.empty();

    private final LocationRepository locations = new LocationRepository() {
        @Override
        public Optional<Location> findByUnLocode(String unLocode) {
            return Optional.of(LOS_ANGELES);
        }

        @Override
        public List<Location> findAll() {
            return List.of(LOS_ANGELES);
        }
    };

    private final RegisterHandlingActivityUseCase useCase = new RegisterHandlingActivityUseCase(
            cargoes, locations, new StubActivities(), new StubCustoms(), notifier(),
            Clock.fixed(NOW, ZoneId.of("Asia/Tokyo")));

    private static HandlingEventNotifier notifier() {
        return new HandlingEventNotifier() {
            @Override
            public void handlingActivityRegistered(
                    com.example.handlingms.application.internal.outboundservices.acl.HandlingActivityRegistered event) {
                // ガードの検査では、発行そのものは見ない
            }

            @Override
            public void customsStatusChanged(
                    com.example.handlingms.application.internal.outboundservices.acl.CustomsStatusChanged event) {
                // ガードの検査では、発行そのものは見ない
            }
        };
    }

    private void declareWith(CustomsStatus status) {
        CustomsDeclaration declared = CustomsDeclaration.declare(
                DeclarationNumber.of("DEC-0001"), CargoBookingId.of(BOOKING),
                HandlingTrackingNumber.of(TRACKING), Instant.parse("2027-09-01T00:00:00Z"));
        declarationsStored.add(status == CustomsStatus.PENDING ? declared
                : declared.updateStatus(status, "tracker01", "理由", NOW));
    }

    private Optional<HandlingActivity> claim() {
        return useCase.register(new RegisterHandlingActivityCommand(
                TRACKING, "CLAIM", "USLAX", NOW, "handler01", null, "田中太郎"));
    }

    /** US29-3。**通関済のときだけ引き取れる**。 */
    @Test
    @DisplayName("通関済なら引き取れる")
    void allowsClaimWhenCleared() {
        declareWith(CustomsStatus.CLEARED);

        assertThat(claim()).isPresent();
        assertThat(registered).hasSize(1);
    }

    /** **現在の通関状態を返す**（US29-3）。「できません」だけでは、次にすることが分からない。 */
    @Test
    @DisplayName("留置のままでは引き取れず、いまの通関状態が伝わる")
    void rejectsClaimWhileHeld() {
        declareWith(CustomsStatus.HELD);

        assertThatThrownBy(this::claim)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("留置");
        assertThat(registered).isEmpty();
    }

    @Test
    @DisplayName("審査中のままでは引き取れない")
    void rejectsClaimWhilePending() {
        declareWith(CustomsStatus.PENDING);

        assertThatThrownBy(this::claim)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("審査中");
    }

    @Test
    @DisplayName("不可のままでは引き取れない")
    void rejectsClaimWhenRejected() {
        declareWith(CustomsStatus.REJECTED);

        assertThatThrownBy(this::claim).isInstanceOf(IllegalStateException.class);
    }

    /**
     * <strong>申告が 1 件も無い貨物も断る。</strong>
     *
     * <p>名簿方式の検査は「載っていないもの」を通すと、載せ忘れたものほど漏れる
     * （過去 take で 3 IT 素通りした形）。申告が無いのは「検査の対象外」ではなく
     * <strong>「通関済でない」</strong>である。
     */
    @Test
    @DisplayName("通関申告が無い貨物は引き取れない")
    void rejectsClaimWithoutAnyDeclaration() {
        assertThatThrownBy(this::claim)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("通関申告がありません");
        assertThat(registered).isEmpty();
    }

    /**
     * <strong>通関のガードは、荷受人の確認より先に効く。</strong>
     *
     * <p>順序には理由がある。<strong>通関で断られるなら、荷受人の確認を集めても
     * 無駄である。</strong>「確認を入れてください」と先に言われた作業員は、荷受人に
     * 署名をもらってから「通関が下りていません」と言われることになる。
     *
     * <p><strong>どちらの検査で落ちたかで判定する</strong>——例外の型と文言を見る
     * （経過時間や副作用の有無では判別しない）。
     *
     * <p>この検査を実 DB の統合テストに置くと、<strong>先に走った検査が作った通関申告</strong>で
     * 前提が崩れ、たまに落ちるテストになる。状態を完全に制御できるここに置く。
     */
    @Test
    @DisplayName("通関が下りていなければ、荷受人の確認を入れる前に断られる")
    void checksCustomsBeforeTheConsigneeConfirmation() {
        RegisterHandlingActivityCommand claimWithoutConfirmation =
                new RegisterHandlingActivityCommand(
                        TRACKING, "CLAIM", "USLAX", NOW, "handler01", null, null);

        assertThatThrownBy(() -> useCase.register(claimWithoutConfirmation))
                .as("荷受人の確認のほうで先に落ちている。順序が入れ替わっている")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("通関申告がありません");
    }

    /**
     * <strong>引取以外の荷役はガードの対象外である。</strong>
     *
     * <p>受領・積込・荷降しまで止めると、通関前の貨物が港で動かせなくなる。
     * 通関は<strong>引き渡しの前提</strong>であって、輸送の前提ではない。
     */
    @Test
    @DisplayName("引取以外の荷役は、通関の状態にかかわらず記録できる")
    void doesNotGuardOtherHandlingTypes() {
        assertThatCode(() -> useCase.register(new RegisterHandlingActivityCommand(
                TRACKING, "UNLOAD", "USLAX", NOW, "handler01", "V0100", null)))
                .doesNotThrowAnyException();
    }

    private final class StubActivities implements HandlingActivityRepository {

        @Override
        public HandlingActivity register(HandlingActivity activity) {
            registered.add(activity);
            return activity;
        }

        @Override
        public List<HandlingActivity> findByBookingId(CargoBookingId bookingId, int limit) {
            return List.copyOf(registered);
        }

        @Override
        public boolean existsSameActivity(CargoBookingId bookingId, HandlingType type,
                String locationUnLocode, Instant completionTime) {
            return false;
        }
    }

    private final class StubCustoms implements CustomsDeclarationRepository {

        @Override
        public CustomsDeclaration save(CustomsDeclaration declaration) {
            declarationsStored.add(declaration);
            return declaration;
        }

        @Override
        public CustomsDeclaration updateStatus(CustomsDeclaration declaration) {
            return declaration;
        }

        @Override
        public Optional<CustomsDeclaration> findById(long declarationId) {
            return Optional.empty();
        }

        @Override
        public Optional<CustomsDeclaration> findUnsettledByTrackingNumber(
                HandlingTrackingNumber trackingNumber) {
            return declarationsStored.stream().filter(candidate -> !candidate.isSettled())
                    .findFirst();
        }

        @Override
        public Optional<CustomsDeclaration> findLatestByBookingId(CargoBookingId cargoBookingId) {
            return declarationsStored.stream().reduce((first, second) -> second);
        }

        @Override
        public long count(String bookingId, String trackingNumber, CustomsStatus status,
                boolean unsettledOnly) {
            return search(bookingId, trackingNumber, status, unsettledOnly, Integer.MAX_VALUE)
                    .size();
        }

        @Override
        public List<CustomsDeclaration> search(String bookingId, String trackingNumber,
                CustomsStatus status, boolean unsettledOnly, int limit) {
            return List.copyOf(declarationsStored);
        }
    }
}
