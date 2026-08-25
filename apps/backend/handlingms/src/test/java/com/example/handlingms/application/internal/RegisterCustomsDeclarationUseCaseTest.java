package com.example.handlingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.handlingms.application.port.CargoSnapshotFinder;
import com.example.handlingms.application.port.CustomsDeclarationRepository;
import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.CargoSnapshot;
import com.example.handlingms.domain.model.CustomsDeclaration;
import com.example.handlingms.domain.model.CustomsStatus;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 通関申告の登録（US29-1・[ADR-025] 決定 7）。
 *
 * <p>ここで守るのは<strong>未決着の申告が 2 件にならない</strong>ことである。
 * 2 件あると、引取のガードがどちらの申告を見ればよいか決まらない。
 *
 * <p><strong>検査は「最新を取る」ことではなく、2 件にならないことを見る</strong>
 * （[ADR-025] 決定 7）。
 */
@DisplayName("通関申告の登録")
class RegisterCustomsDeclarationUseCaseTest {

    private static final String TRACKING = "TRK-20260823-0001";
    private static final Instant DECLARED_AT = Instant.parse("2027-09-02T00:00:00Z");

    private final List<CustomsDeclaration> stored = new ArrayList<>();

    private final CustomsDeclarationRepository declarations = new StubRepository();

    private final CargoSnapshotFinder cargoes = trackingNumber ->
            TRACKING.equals(trackingNumber.value())
                    ? Optional.of(CargoSnapshot.of("BKG-2026000001", "JPTYO", "USLAX", List.of()))
                    : Optional.empty();

    private final RegisterCustomsDeclarationUseCase useCase =
            new RegisterCustomsDeclarationUseCase(declarations, cargoes);

    private CustomsDeclaration register(String declarationNumber) {
        return useCase.register(new RegisterCustomsDeclarationCommand(
                TRACKING, declarationNumber, DECLARED_AT, null, "handler01"));
    }

    @Test
    @DisplayName("登録すると、審査中の申告ができる")
    void registersAPendingDeclaration() {
        CustomsDeclaration declared = register("DEC-0001");

        assertThat(declared.status()).isEqualTo(CustomsStatus.PENDING);
        assertThat(declared.cargoBookingId().value()).isEqualTo("BKG-2026000001");
    }

    /** 打ち間違いが最も多い。**何を直せばよいかを伝える**。 */
    @Test
    @DisplayName("知らない追跡番号は断る")
    void rejectsAnUnknownTrackingNumber() {
        RegisterCustomsDeclarationCommand unknown = new RegisterCustomsDeclarationCommand(
                "TRK-20260823-9999", "DEC-0001", DECLARED_AT, null, "handler01");

        assertThatThrownBy(() -> useCase.register(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("見つかりません");
    }

    /** <strong>[ADR-025] 決定 7。未決着の申告があるあいだは 2 通目を受け付けない。</strong> */
    @Test
    @DisplayName("審査中の申告があるあいだは、2 通目を断る")
    void rejectsASecondPendingDeclaration() {
        register("DEC-0001");

        assertThatThrownBy(() -> register("DEC-0002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("決着していない");
        assertThat(stored).hasSize(1);
    }

    @Test
    @DisplayName("留置の申告があるあいだも、2 通目を断る")
    void rejectsASecondDeclarationWhileHeld() {
        CustomsDeclaration first = register("DEC-0001");
        replace(first.updateStatus(CustomsStatus.HELD, "tracker01", "書類不備", DECLARED_AT));

        assertThatThrownBy(() -> register("DEC-0002"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stored).hasSize(1);
    }

    /** 書類を直して出し直すのは実務にある。 */
    @Test
    @DisplayName("不可になったあとは、出し直せる")
    void allowsReDeclarationAfterRejected() {
        CustomsDeclaration first = register("DEC-0001");
        replace(first.updateStatus(CustomsStatus.REJECTED, "tracker01", "不備", DECLARED_AT));

        assertThat(register("DEC-0002").status()).isEqualTo(CustomsStatus.PENDING);
        assertThat(stored).hasSize(2);
    }

    /** 通関済の貨物に新しい申告は要らない。**引き取れる状態を、あとからの申告で覆さない**。 */
    @Test
    @DisplayName("通関済のあとは、出し直せない")
    void rejectsAfterCleared() {
        CustomsDeclaration first = register("DEC-0001");
        replace(first.updateStatus(CustomsStatus.CLEARED, "tracker01", "完了", DECLARED_AT));

        assertThatThrownBy(() -> register("DEC-0002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("通関済");
        assertThat(stored).hasSize(1);
    }

    private void replace(CustomsDeclaration updated) {
        stored.removeIf(candidate ->
                candidate.declarationNumber().equals(updated.declarationNumber()));
        stored.add(updated);
    }

    /** 保存先の代役。**未決着の判定は集約の述語をそのまま呼ぶ**（別実装にしない）。 */
    private final class StubRepository implements CustomsDeclarationRepository {

        @Override
        public CustomsDeclaration save(CustomsDeclaration declaration) {
            stored.add(declaration);
            return declaration;
        }

        @Override
        public CustomsDeclaration updateStatus(CustomsDeclaration declaration) {
            replace(declaration);
            return declaration;
        }

        @Override
        public Optional<CustomsDeclaration> findById(long declarationId) {
            return Optional.empty();
        }

        @Override
        public Optional<CustomsDeclaration> findUnsettledByTrackingNumber(
                HandlingTrackingNumber trackingNumber) {
            return stored.stream()
                    .filter(candidate -> candidate.trackingNumber().equals(trackingNumber))
                    .filter(candidate -> !candidate.isSettled())
                    .findFirst();
        }

        @Override
        public Optional<CustomsDeclaration> findLatestByBookingId(CargoBookingId cargoBookingId) {
            return stored.stream()
                    .filter(candidate -> candidate.cargoBookingId().equals(cargoBookingId))
                    .reduce((first, second) -> second);
        }

        @Override
        public List<CustomsDeclaration> search(String bookingId, String trackingNumber,
                CustomsStatus status, int limit) {
            return List.copyOf(stored);
        }
    }
}
