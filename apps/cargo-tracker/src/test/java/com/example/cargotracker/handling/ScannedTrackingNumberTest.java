package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.handling.domain.model.CargoBookingId;
import com.example.cargotracker.handling.domain.model.HandledCargo;
import com.example.cargotracker.handling.domain.model.HandlingActivity;
import com.example.cargotracker.handling.domain.model.HandlingDetails;
import com.example.cargotracker.handling.domain.model.RegisterHandlingCommand;
import com.example.cargotracker.handling.domain.model.ScannedTrackingNumber;
import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 荷役作業は<strong>読み取った追跡番号</strong>を持つ（IT6 レビュー H12）。
 *
 * <p>IT6 の {@code RegisterHandlingCommand} は「集約が知る必要があるのは
 * 『どの予約に対する作業か』だけである」として追跡番号を持たせていなかった。
 * <strong>本 IT でその判断を変える。</strong>
 *
 * <p>変える理由は 2 つある。
 *
 * <ol>
 *   <li><strong>読み取った番号は作業そのものの事実である。</strong> 予約への参照ではない。
 *       誤って別の貨物の番号を読み取った場合、<strong>誤った番号がそのまま残るほうが
 *       追跡できる</strong>。予約 ID から逆算すると、誤読の痕跡が消える</li>
 *   <li>作業員が手にしているのは追跡番号だけであり、予約 ID は紙にもラベルにも無い。
 *       一覧に予約 ID しか出ないと、<strong>自分が今登録した作業を探せない</strong></li>
 * </ol>
 *
 * <p>Booking の {@code BookingTrackingNumber} も Tracking の {@code TrackingNumber} も
 * 参照しない（コンテキスト分離。{@code HandlingVoyageNumber} と同じ形）。
 */
@DisplayName("読み取った追跡番号（H12）")
class ScannedTrackingNumberTest {

    private static HandlingActivity 荷役(String scanned) {
        return HandlingActivity.register(new RegisterHandlingCommand(
                new HandledCargo(new ScannedTrackingNumber(scanned),
                        new CargoBookingId(UUID.randomUUID())),
                HandlingDetails.receive(),
                Instant.parse("2026-09-03T01:00:00Z"),
                Location.of("JPOSA"),
                null, "港湾太郎"));
    }

    @Test
    void 読み取った番号が作業に残る() {
        assertThat(荷役("TRK-20260901-0001").scannedTrackingNumber().value())
                .isEqualTo("TRK-20260901-0001");
    }

    /**
     * <strong>番号は必須である。</strong> 読み取らずに登録する経路は無い
     * （画面も ACL も追跡番号から予約を引き当てる）。
     * 空を許すと、一覧に空欄の行が並んで「読み取り忘れ」と「古い記録」の区別がつかない。
     */
    @Test
    void 番号の無い荷役は登録できない() {
        assertThatThrownBy(() -> new ScannedTrackingNumber(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("追跡番号");
    }

    /**
     * <strong>形を作り変えない。</strong> 誤読した番号を「正しそうな形」に直すと、
     * 別の貨物の作業として記録されうる。前後の空白を落とすだけにとどめる。
     */
    @Test
    void 形の違う番号もそのまま残す() {
        assertThat(new ScannedTrackingNumber("  TRK-2026-1  ").value())
                .isEqualTo("TRK-2026-1");
    }
}
