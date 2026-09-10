package com.example.cargotracker.tracking.application.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.CustomsStatusChangedEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityVoidedEvent;
import com.example.cargotracker.tracking.domain.model.commands.AdvanceTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.RegisterTrackingExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.ResolveTrackingExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.RevertTrackingCommand;
import com.example.cargotracker.tracking.domain.model.entities.TrackingException;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.commandhandling.gateway.CommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 荷役 → 貨物状態の連鎖（US15 §受入基準 4 / IT9 T6）。
 *
 * <p><b>スタブは受け取ったコマンドを捨てない。</b> 捨てると、組み立てを潰しても
 * 緑のままになる（IT6 の実測欠陥）。とくに {@code finalPort} と {@code offRoute}
 * はどちらも boolean で、<b>入れ替えても型では気づけない</b>——目的港での荷降しが
 * 誤配になり、予定外の荷役が引取待ちになる。</p>
 */
class TrackingReactionHandlerTest {

    private static final Instant HANDLED = Instant.parse("2026-09-10T02:00:00Z");
    private static final Instant RECORDED = Instant.parse("2026-09-10T03:00:00Z");

    private final List<Object> sent = new ArrayList<>();
    private TrackingReactionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TrackingReactionHandler(gateway());
    }

    private CommandGateway gateway() {
        return new CommandGateway() {
            @Override
            public CommandResult send(Object command,
                    org.axonframework.messaging.core.Metadata metadata,
                    org.axonframework.messaging.core.unitofwork.ProcessingContext context) {
                sent.add(command);
                return () -> CompletableFuture.completedFuture(null);
            }

            @Override
            public void describeTo(
                    org.axonframework.common.infra.ComponentDescriptor descriptor) {
                // 検査には要らない。
            }
        };
    }

    private static HandlingActivityRegisteredEvent registered(boolean offRoute,
            boolean finalPort) {
        return new HandlingActivityRegisteredEvent("act-1", "TRK-8K2QX7M4RB", "b-1", "UNLOAD",
                "USNYC", "V-MOL-001", offRoute, finalPort, "handler01", HANDLED, RECORDED);
    }

    @Test
    @DisplayName("US15 §4: 荷役が記録されたら貨物状態を進めるよう送る")
    void sendsAdvanceTracking() {
        handler.on(registered(false, true));

        assertThat(sent).singleElement()
                .isEqualTo(new AdvanceTrackingCommand("TRK-8K2QX7M4RB", "act-1", "UNLOAD",
                        "USNYC", true, false, "handler01", HANDLED));
    }

    @Test
    @DisplayName("目的港か・予定外かを取り違えない（どちらも boolean で型では気づけない）")
    void doesNotSwapFinalPortAndOffRoute() {
        handler.on(registered(true, false));

        assertThat(sent).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(AdvanceTrackingCommand.class))
                .satisfies(command -> {
                    assertThat(command.offRoute()).as("予定外の荷役が誤配にならない").isTrue();
                    assertThat(command.finalPort()).as("途中の港が目的港になる").isFalse();
                });
    }

    @Test
    @DisplayName("不変条件 11: 荷役が取り消されたら戻すよう送る（どの荷役かを名指しで）")
    void sendsRevertTracking() {
        // **どの荷役かを送らないと、最後でない荷役の取り消しで巻き戻せてしまう。**
        handler.on(new HandlingActivityVoidedEvent("act-1", "TRK-8K2QX7M4RB", "b-1", "UNLOAD",
                "取り違えました", "handler01", RECORDED));

        assertThat(sent).singleElement()
                .isEqualTo(new RevertTrackingCommand("TRK-8K2QX7M4RB", "act-1", "UNLOAD",
                        "取り違えました", "handler01", RECORDED));
    }
    private static CustomsStatusChangedEvent customs(String previous, String status) {
        return new CustomsStatusChangedEvent("IMP-2026-0001", "TRK-8K2QX7M4RB", "b-1",
                previous, status, "原産地証明が未提出", 0, "tracker01", RECORDED);
    }

    @Test
    @DisplayName("US29 §5: 留置になると税関保留を起票する（手では起票できない種別）")
    void registersCustomsHoldWhenHeld() {
        handler.on(customs("PENDING", "HELD"));

        assertThat(sent).singleElement()
                .isInstanceOfSatisfying(RegisterTrackingExceptionCommand.class, command -> {
                    assertThat(command.type()).isEqualTo(ExceptionType.CUSTOMS_HOLD);
                    assertThat(command.trackingNumber()).isEqualTo("TRK-8K2QX7M4RB");
                    assertThat(command.description()).contains("IMP-2026-0001")
                            .contains("原産地証明が未提出");
                });
    }

    @Test
    @DisplayName("識別子は申告から導く（留置が再配送されても例外が増えない）")
    void derivesTheExceptionIdFromTheDeclaration() {
        handler.on(customs("PENDING", "HELD"));
        handler.on(customs("PENDING", "HELD"));

        assertThat(sent).hasSize(2)
                .extracting(command -> ((RegisterTrackingExceptionCommand) command).exceptionId())
                .containsOnly(TrackingException.customsHoldIdFor("IMP-2026-0001"));
    }

    @Test
    @DisplayName("**起票の後段を数える**: 留置から出たら解決する（Try T1）")
    void resolvesCustomsHoldWhenLeavingHeld() {
        // **起票だけでは足りない。** 例外中の貨物は荷役を預かって適用しない
        // （IT11 引き継ぎ枠 B）ので、解決しないと通関済にしても引取が届かない。
        handler.on(customs("HELD", "CLEARED"));

        assertThat(sent).singleElement()
                .isInstanceOfSatisfying(ResolveTrackingExceptionCommand.class, command ->
                        assertThat(command.exceptionId())
                                .isEqualTo(TrackingException.customsHoldIdFor("IMP-2026-0001")));
    }

    @Test
    @DisplayName("不可でも解決する（通らなかったことは別の業務で扱う）")
    void resolvesCustomsHoldWhenRejected() {
        handler.on(customs("HELD", "REJECTED"));

        assertThat(sent).singleElement()
                .isInstanceOf(ResolveTrackingExceptionCommand.class);
    }

    @Test
    @DisplayName("留置を経ていない通関済では解決しない（起票していない例外は解決できない）")
    void doesNotResolveWhenNeverHeld() {
        // **クラスタ E2E が見つけた実欠陥の回帰テスト**（IT12 T7e）。
        // 新しい状態だけを見て「通関済なら解決」と判定していたので、
        // 一度も留置にならず通関済になった申告（審査中 → 通関済）でも
        // 解決コマンドを送っていた。集約は正しく断るが、その例外が退避され、
        // **列が全体で 1 本なので後続のイベントが全部退避された**
        // （[ADR-0014] の「引き受けていないこと」）。追跡番号発行の連鎖が止まった。
        //
        // **層ごとの検査では出なかった。** ここは「どのコマンドを送るか」だけを
        // 見ており、集約が受け付けるかは判別しない。
        handler.on(customs("PENDING", "CLEARED"));

        assertThat(sent)
                .as("留置していないなら、解決する対象が無い")
                .isEmpty();
    }

    @Test
    @DisplayName("留置から審査中へ戻っても解決する（留置を出た時点で保留は終わっている）")
    void resolvesWhenLeavingHeldEvenToPending() {
        // **判定は「留置を出たか」であって「どこへ行ったか」ではない。**
        // 集約は留置 → 審査中を断らないので（未決着どうしの遷移）、この経路は
        // 起こりうる。留置でなくなった時点で税関保留の理由は消えている。
        handler.on(customs("HELD", "PENDING"));

        assertThat(sent).singleElement()
                .isInstanceOf(ResolveTrackingExceptionCommand.class);
    }

    @Test
    @DisplayName("留置に入っていない状態どうしの変化では何もしない")
    void doesNothingBetweenNonHeldStatuses() {
        handler.on(customs("PENDING", "REJECTED"));

        assertThat(sent).isEmpty();
    }
}
