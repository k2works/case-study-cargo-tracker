package com.example.trackingms.interfaces.events;

import com.example.shared.events.TrackingIssuanceRequestedEvent;
import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.services.TrackingNumberGenerator;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TrackingIssuanceRequestedEventHandler} のユニットテスト（IT5 1.4 / ADR-0011）。
 *
 * <p>cross-service 受信ハンドラの責務:</p>
 * <ul>
 *   <li>採番された {@link TrackingNumber} で {@link InitializeTrackingCommand} を発行する</li>
 *   <li>ホワイトリスト 2 種（{@link AggregateNotFoundException} / {@link CommandExecutionException}）は
 *       冪等にスキップして伝播させない</li>
 *   <li>それ以外の例外は伝播させる（ADR-0011 ホワイトリスト方式の境界を Negative テストで担保）</li>
 * </ul>
 */
class TrackingIssuanceRequestedEventHandlerTest {

    private CommandGateway commandGateway;
    private TrackingNumberGenerator generator;
    private TrackingIssuanceRequestedEventHandler handler;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        generator = mock(TrackingNumberGenerator.class);
        when(generator.generate()).thenReturn(TrackingNumber.of("TRK-AB12CD3456"));
        handler = new TrackingIssuanceRequestedEventHandler(commandGateway, generator);
    }

    private TrackingIssuanceRequestedEvent event(String bookingId) {
        return new TrackingIssuanceRequestedEvent(
                bookingId, "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL",
                List.of());
    }

    @Test
    @DisplayName("US14: 追跡発行依頼を受けて InitializeTrackingCommand を発行する")
    void 追跡初期化コマンドを発行する() {
        handler.on(event("B-001"));

        var captor = forClass(InitializeTrackingCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        InitializeTrackingCommand sent = captor.getValue();
        assertThat(sent.bookingId()).isEqualTo("B-001");
        assertThat(sent.trackingNumber()).isEqualTo("TRK-AB12CD3456");
    }

    @Test
    @DisplayName("ADR-0011 Positive: AggregateNotFoundException は冪等スキップ（伝播しない）")
    void 集約不在は伝播しない() {
        when(commandGateway.sendAndWait(any(InitializeTrackingCommand.class)))
                .thenThrow(new AggregateNotFoundException("TRK-AB12CD3456", "対象集約が存在しません"));

        assertThatCode(() -> handler.on(event("B-001"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADR-0011 Positive: CommandExecutionException も冪等スキップ（伝播しない）")
    void 状態ガード違反は伝播しない() {
        when(commandGateway.sendAndWait(any(InitializeTrackingCommand.class)))
                .thenThrow(new CommandExecutionException("既に初期化済み", null));

        assertThatCode(() -> handler.on(event("B-001"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADR-0011 Negative: ホワイトリスト対象外の例外は握り潰さず伝播する")
    void 冪等スキップ対象外の例外は伝播する() {
        when(commandGateway.sendAndWait(any(InitializeTrackingCommand.class)))
                .thenThrow(new RuntimeException("予期しない基盤エラー"));

        assertThatThrownBy(() -> handler.on(event("B-001")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("予期しない基盤エラー");
    }
}
