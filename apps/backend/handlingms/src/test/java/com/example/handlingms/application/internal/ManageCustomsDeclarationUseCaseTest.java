package com.example.handlingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.handlingms.application.port.CustomsDeclarationRepository;
import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.CustomsDeclaration;
import com.example.handlingms.domain.model.CustomsStatus;
import com.example.handlingms.domain.model.DeclarationNumber;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 通関申告の閲覧と状態更新（US29-2・US29-6・US29-7）。
 *
 * <p><strong>テストも同じ時計で「今日」を決める</strong>（過去 take の教訓）。
 * 実行環境の既定時刻を使うと、CI（UTC）でだけ落ちるテストになる。
 */
@DisplayName("通関申告の管理")
class ManageCustomsDeclarationUseCaseTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    /** 業務の「いま」。**進めない時計**にして、判定が時刻に揺れないようにする。 */
    private final Clock clock = Clock.fixed(Instant.parse("2027-09-10T00:00:00Z"), ZONE);

    private final List<CustomsDeclaration> stored = new ArrayList<>();

    private final CustomsDeclarationRepository declarations = new StubRepository();

    /** 発行されたイベント。**発行したことを検査から見る**。 */
    private final List<com.example.handlingms.application.port.CustomsStatusChanged> published =
            new ArrayList<>();

    private final com.example.handlingms.application.port.HandlingEventNotifier notifier =
            new com.example.handlingms.application.port.HandlingEventNotifier() {
                @Override
                public void handlingActivityRegistered(
                        com.example.handlingms.application.port.HandlingActivityRegistered event) {
                    throw new UnsupportedOperationException("この検査では使わない");
                }

                @Override
                public void customsStatusChanged(
                        com.example.handlingms.application.port.CustomsStatusChanged event) {
                    published.add(event);
                }
            };

    private final ManageCustomsDeclarationUseCase useCase =
            new ManageCustomsDeclarationUseCase(declarations, notifier, clock);

    private CustomsDeclaration heldSince(String number, String heldAt) {
        CustomsDeclaration declaration = CustomsDeclaration.declare(
                        DeclarationNumber.of(number), CargoBookingId.of("BKG-2026000001"),
                        HandlingTrackingNumber.of("TRK-20260823-0001"),
                        Instant.parse("2027-09-01T00:00:00Z"))
                .updateStatus(CustomsStatus.HELD, "tracker01", "書類不備", Instant.parse(heldAt));
        stored.add(declaration);
        return declaration;
    }

    @Test
    @DisplayName("業務の暦は注入した時計から決まる")
    void takesTodayFromTheInjectedClock() {
        assertThat(useCase.today()).isEqualTo(LocalDate.of(2027, Month.SEPTEMBER, 10));
        assertThat(useCase.zone()).isEqualTo(ZONE);
    }

    /**
     * US29-6。<strong>3 日ちょうどは数えない。</strong>
     *
     * <p>境界を入れると督促の対象が 1 日早まる。判定は集約の述語をそのまま呼ぶ
     * ——ここで日数を数え直すと、督促の規則が 2 か所になる。
     */
    @Test
    @DisplayName("留置 3 日超だけを数える")
    void countsOnlyDeclarationsHeldForMoreThanThreeDays() {
        heldSince("DEC-0001", "2027-09-05T00:00:00Z");  // 5 日経過 → 対象
        heldSince("DEC-0002", "2027-09-07T00:00:00Z");  // 3 日ちょうど → 対象外
        heldSince("DEC-0003", "2027-09-09T00:00:00Z");  // 1 日 → 対象外

        assertThat(useCase.countHeldOverdue()).isEqualTo(1);
    }

    @Test
    @DisplayName("状態を更新すると、更新後の申告が返る")
    void updatesTheStatus() {
        CustomsDeclaration declared = CustomsDeclaration.declare(
                DeclarationNumber.of("DEC-0010"), CargoBookingId.of("BKG-2026000001"),
                HandlingTrackingNumber.of("TRK-20260823-0001"),
                Instant.parse("2027-09-01T00:00:00Z"));
        stored.add(declared);

        CustomsDeclaration updated = useCase
                .updateStatus(1L, "CLEARED", "tracker01", "書類確認により通関完了")
                .orElseThrow();

        assertThat(updated.status()).isEqualTo(CustomsStatus.CLEARED);
        // **更新日時も注入した時計から決まる。**実行時刻を使うと、記録が実行環境に依存する
        assertThat(updated.clearedAt()).contains(clock.instant());
    }

    /** 読み方は列挙が持つ。**入口ごとに valueOf を書かない**。 */
    @Test
    @DisplayName("状態の名前が不正なら断る")
    void rejectsAnUnknownStatusName() {
        assertThatThrownBy(() -> useCase.search(null, null, "UNKNOWN", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("通関状態が不正です");
    }

    /**
     * US29-5。<strong>留め置かれたことを知らせる。</strong>
     *
     * <p>知らせないと、留置は追跡管理者の未解決一覧に現れず、貨物はそのまま止まる。
     * <strong>理由も載せる</strong>——税関に問い合わせるときの手がかりになる。
     */
    @Test
    @DisplayName("状態を更新すると、通関状態が変わったことを知らせる")
    void announcesTheStatusChange() {
        CustomsDeclaration declared = CustomsDeclaration.declare(
                DeclarationNumber.of("DEC-0020"), CargoBookingId.of("BKG-2026000001"),
                HandlingTrackingNumber.of("TRK-20260823-0001"),
                Instant.parse("2027-09-01T00:00:00Z"));
        stored.add(declared);

        useCase.updateStatus(1L, "HELD", "tracker01", "書類不備");

        assertThat(published).hasSize(1);
        assertThat(published.getFirst().toStatus()).isEqualTo("HELD");
        assertThat(published.getFirst().fromStatus()).isEqualTo("PENDING");
        assertThat(published.getFirst().reason()).isEqualTo("書類不備");
        assertThat(published.getFirst().trackingNumber()).isEqualTo("TRK-20260823-0001");
    }

    @Test
    @DisplayName("知らない申告 ID は空で返る")
    void returnsEmptyForAnUnknownDeclaration() {
        assertThat(useCase.find(999L)).isEmpty();
    }

    /** 保存先の代役。**判定は集約の述語をそのまま呼ぶ**（別実装にしない）。 */
    private final class StubRepository implements CustomsDeclarationRepository {

        @Override
        public CustomsDeclaration save(CustomsDeclaration declaration) {
            stored.add(declaration);
            return declaration;
        }

        @Override
        public CustomsDeclaration updateStatus(CustomsDeclaration declaration) {
            return declaration;
        }

        @Override
        public Optional<CustomsDeclaration> findById(long declarationId) {
            return declarationId == 1L && !stored.isEmpty()
                    ? Optional.of(stored.getFirst()) : Optional.empty();
        }

        @Override
        public Optional<CustomsDeclaration> findUnsettledByTrackingNumber(
                HandlingTrackingNumber trackingNumber) {
            return stored.stream().filter(candidate -> !candidate.isSettled()).findFirst();
        }

        @Override
        public Optional<CustomsDeclaration> findLatestByBookingId(CargoBookingId cargoBookingId) {
            return stored.stream().reduce((first, second) -> second);
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
            return stored.stream()
                    .filter(candidate -> status == null || candidate.status() == status)
                    .toList();
        }
    }
}
