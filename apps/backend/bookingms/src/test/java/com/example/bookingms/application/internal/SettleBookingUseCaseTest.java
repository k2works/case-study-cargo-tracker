package com.example.bookingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.Cargo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 精算の完了を受けて予約を閉じる（US23-4・[ADR-028] 決定 1）。
 *
 * <p><strong>相手が「精算が閉じた」と信じる根拠である。</strong>荷役の購読が
 * 「知らない追跡番号でも止めない」のとは立場が違う——黙って捨てると、引取済のまま
 * 残った予約に誰も気づけない。
 */
@DisplayName("予約の精算完了")
class SettleBookingUseCaseTest {

    private CargoRepository cargoes;

    private SettleBookingUseCase useCase;

    @BeforeEach
    void setUp() {
        cargoes = mock(CargoRepository.class);
        useCase = new SettleBookingUseCase(cargoes);
    }

    private static Cargo delivered() {
        Cargo cargo = mock(Cargo.class);
        when(cargo.isSettled()).thenReturn(false);
        when(cargo.settle()).thenAnswer(invocation -> cargo);
        return cargo;
    }

    @Test
    @DisplayName("引取済の予約を精算済にして保存する")
    void settlesAndSaves() {
        Cargo cargo = delivered();
        CargoSummary summary = mock(CargoSummary.class);
        when(summary.cargo()).thenReturn(cargo);
        when(cargoes.findByBookingId("BKG-2026000007")).thenReturn(Optional.of(summary));

        useCase.settle("BKG-2026000007");

        verify(cargo).settle();
        ArgumentCaptor<Cargo> saved = ArgumentCaptor.forClass(Cargo.class);
        verify(cargoes).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(cargo);
    }

    /**
     * <strong>すでに精算済なら、何もせず成功として返す（冪等）</strong>
     * （[ADR-028] 決定 1・IT12 レビュー architect 高 1）。
     *
     * <p>相手（billingms）は入金の記録と同じ取引の中でこれを呼ぶ。通知が届いたあとに
     * 相手側が失敗すると、予約だけが精算済で請求書は未入金のまま残る——そこで断ると、
     * <strong>経理担当者は何度押しても入金を記録できない</strong>。
     */
    @Test
    @DisplayName("すでに精算済の予約は、何もせず受け入れる")
    void acceptsAlreadySettledBookings() {
        Cargo cargo = mock(Cargo.class);
        when(cargo.isSettled()).thenReturn(true);
        CargoSummary summary = mock(CargoSummary.class);
        when(summary.cargo()).thenReturn(cargo);
        when(cargoes.findByBookingId("BKG-2026000007")).thenReturn(Optional.of(summary));

        useCase.settle("BKG-2026000007");

        verify(cargo, never()).settle();
        verify(cargoes, never()).save(any());
    }

    /**
     * <strong>知らない予約は断る。</strong>
     *
     * <p>黙って受け取ると、billingms 側は「予約が閉じた」と信じたまま先へ進む。
     */
    @Test
    @DisplayName("知らない予約は断り、何も保存しない")
    void rejectsUnknownBookings() {
        when(cargoes.findByBookingId("BKG-9999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.settle("BKG-9999999999"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cargoes, never()).save(any());
    }

    /** <strong>引取が終わっていなければ集約が断る。</strong>運んでいない予約に精算済は無い。 */
    @Test
    @DisplayName("引取が終わっていない予約は、集約が断る")
    void leavesTheDecisionToTheAggregate() {
        Cargo cargo = mock(Cargo.class);
        when(cargo.isSettled()).thenReturn(false);
        when(cargo.settle()).thenThrow(new IllegalStateException("引取が終わっていません"));
        CargoSummary summary = mock(CargoSummary.class);
        when(summary.cargo()).thenReturn(cargo);
        when(cargoes.findByBookingId("BKG-2026000001")).thenReturn(Optional.of(summary));

        assertThatThrownBy(() -> useCase.settle("BKG-2026000001"))
                .isInstanceOf(IllegalStateException.class);

        verify(cargoes, never()).save(any());
    }

    /** 正典の遷移（引取済 → 精算済）を、集約の述語でそのまま確かめる。 */
    @Test
    @DisplayName("精算済へ進めるのは引取済だけである")
    void onlyDeliveredCanSettle() {
        assertThat(BookingStatus.DELIVERED.canAdvanceTo(BookingStatus.SETTLED)).isTrue();
        assertThat(BookingStatus.CANCELLED.canAdvanceTo(BookingStatus.SETTLED)).isFalse();
    }
}
