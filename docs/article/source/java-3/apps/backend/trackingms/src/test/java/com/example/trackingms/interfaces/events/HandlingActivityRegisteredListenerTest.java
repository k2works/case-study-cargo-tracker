package com.example.trackingms.interfaces.events;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.example.trackingms.application.internal.commandservices.AdvanceTrackingUseCase;
import com.example.trackingms.application.internal.commandservices.DetectMisrouteUseCase;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * 荷役の購読（US15-4・US28-2・[ADR-026] 決定 1）。
 *
 * <p><strong>1 つのイベントで 2 つのユースケースを呼ぶ。</strong>この形は IT10 で
 * できたが、<strong>順序の意味はコメントでしか書かれていなかった</strong>
 * ——「先に状態を進めてから起票する」と説明してあるだけで、逆にしても何も落ちない。
 * IT11 返済枠 0.8 で、壊すと赤になる形にした。
 */
@DisplayName("荷役の購読")
class HandlingActivityRegisteredListenerTest {

    private final AdvanceTrackingUseCase advanceTracking = mock(AdvanceTrackingUseCase.class);
    private final DetectMisrouteUseCase detectMisroute = mock(DetectMisrouteUseCase.class);
    private final HandlingActivityRegisteredListener listener =
            new HandlingActivityRegisteredListener(advanceTracking, detectMisroute);

    /**
     * <strong>先に状態を進めてから、誤配を起票する</strong>（US28-2）。
     *
     * <p>順序を逆にすると、例外を起票した直後に荷役の状態で上書きされ、
     * <strong>未解決の例外が一覧から消える</strong>。追跡管理者は誤配に気づけない。
     *
     * <p>呼んだ回数だけを見ると、逆順にしても緑になる。順序そのものを見る。
     */
    @Test
    @DisplayName("状態を進めてから、誤配を起票する")
    void advancesTheStatusBeforeRaisingTheMisroute() {
        listener.onHandlingActivityRegistered(new HandlingActivityRegisteredMessage(
                "TRK-20260823-0003", "BKG-2026000003", "UNLOAD", "SGSIN",
                Instant.parse("2027-09-09T00:00:00Z"), "V0200", true,
                Instant.parse("2027-09-09T00:05:00Z")));

        InOrder order = inOrder(advanceTracking, detectMisroute);
        order.verify(advanceTracking).advance("TRK-20260823-0003", "UNLOAD", "SGSIN",
                Instant.parse("2027-09-09T00:00:00Z"));
        order.verify(detectMisroute).onHandlingActivityRegistered("TRK-20260823-0003", "SGSIN",
                Instant.parse("2027-09-09T00:00:00Z"), true);
        order.verifyNoMoreInteractions();
    }

    /**
     * <strong>予定どおりの荷役でも、両方を呼ぶ</strong>（[ADR-026] 決定 1）。
     *
     * <p>{@code offRoute} が false のときに呼ばない実装にすると、
     * <strong>誤配から復帰したことを誰も知らない</strong>。判定はユースケース側が持つ。
     */
    @Test
    @DisplayName("予定どおりの荷役でも、両方のユースケースを呼ぶ")
    void callsBothEvenWhenTheHandlingIsOnRoute() {
        listener.onHandlingActivityRegistered(new HandlingActivityRegisteredMessage(
                "TRK-20260823-0002", "BKG-2026000002", "LOAD", "JPTYO",
                Instant.parse("2027-09-01T00:00:00Z"), "V0100", false,
                Instant.parse("2027-09-01T00:05:00Z")));

        InOrder order = inOrder(advanceTracking, detectMisroute);
        order.verify(advanceTracking).advance("TRK-20260823-0002", "LOAD", "JPTYO",
                Instant.parse("2027-09-01T00:00:00Z"));
        order.verify(detectMisroute).onHandlingActivityRegistered("TRK-20260823-0002", "JPTYO",
                Instant.parse("2027-09-01T00:00:00Z"), false);
        order.verifyNoMoreInteractions();
    }
}
