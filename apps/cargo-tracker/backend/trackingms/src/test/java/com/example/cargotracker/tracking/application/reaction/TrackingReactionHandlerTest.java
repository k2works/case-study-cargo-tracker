package com.example.cargotracker.tracking.application.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityVoidedEvent;
import com.example.cargotracker.tracking.domain.model.commands.AdvanceTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.RevertTrackingCommand;
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
}
