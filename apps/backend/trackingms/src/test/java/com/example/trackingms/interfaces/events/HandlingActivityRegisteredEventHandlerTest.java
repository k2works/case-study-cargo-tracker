package com.example.trackingms.interfaces.events;

import com.example.shared.events.HandlingActivityRegisteredEvent;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import com.example.trackingms.domain.model.TransportStatus;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link HandlingActivityRegisteredEventHandler} のユニットテスト（US15・US16 / IT5 3.3）。
 *
 * <p>HandlingType → TransportStatus マッピング、ADR-0011 ホワイトリスト方式の例外スキップ、
 * CUSTOMS など状態変更なしの種別の扱いを検証する。</p>
 */
class HandlingActivityRegisteredEventHandlerTest {

    private CommandGateway commandGateway;
    private HandlingActivityRegisteredEventHandler handler;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        handler = new HandlingActivityRegisteredEventHandler(commandGateway);
    }

    private HandlingActivityRegisteredEvent event(String handlingType) {
        return new HandlingActivityRegisteredEvent(
                "A-001", "TRK-AB12CD3456", handlingType,
                LocalDateTime.of(2026, 7, 20, 10, 0),
                "JPTYO", "V-MAERSK-220", "H-001",
                null, false);
    }

    @Test
    @DisplayName("US15: RECEIVE → RECEIVED の UpdateTransportStatusCommand を送信")
    void RECEIVEで状態をRECEIVEDに更新する() {
        handler.on(event("RECEIVE"));

        ArgumentCaptor<UpdateTransportStatusCommand> captor =
                ArgumentCaptor.forClass(UpdateTransportStatusCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        UpdateTransportStatusCommand sent = captor.getValue();
        assertThat(sent.trackingNumber()).isEqualTo("TRK-AB12CD3456");
        assertThat(sent.toStatus()).isEqualTo(TransportStatus.RECEIVED);
    }

    @Test
    @DisplayName("US15: LOAD → LOADED")
    void LOADで状態をLOADEDに更新する() {
        handler.on(event("LOAD"));
        ArgumentCaptor<UpdateTransportStatusCommand> captor =
                ArgumentCaptor.forClass(UpdateTransportStatusCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue().toStatus()).isEqualTo(TransportStatus.LOADED);
    }

    @Test
    @DisplayName("US15: UNLOAD → UNLOADED")
    void UNLOADで状態をUNLOADEDに更新する() {
        handler.on(event("UNLOAD"));
        ArgumentCaptor<UpdateTransportStatusCommand> captor =
                ArgumentCaptor.forClass(UpdateTransportStatusCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue().toStatus()).isEqualTo(TransportStatus.UNLOADED);
    }

    @Test
    @DisplayName("US16: CLAIM → DELIVERED")
    void CLAIMで状態をDELIVEREDに更新する() {
        handler.on(event("CLAIM"));
        ArgumentCaptor<UpdateTransportStatusCommand> captor =
                ArgumentCaptor.forClass(UpdateTransportStatusCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue().toStatus()).isEqualTo(TransportStatus.DELIVERED);
    }

    @Test
    @DisplayName("CUSTOMS は本イテレーションでは状態変更なし（コマンド発行しない）")
    void CUSTOMSではコマンドを送信しない() {
        handler.on(event("CUSTOMS"));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("ADR-0011: AggregateNotFoundException は WARN スキップする")
    void AggregateNotFoundExceptionをスキップする() {
        doThrow(new AggregateNotFoundException("TRK-AB12CD3456", "not found"))
                .when(commandGateway).sendAndWait(any());

        // 例外が伝播せずスキップされる
        handler.on(event("RECEIVE"));
    }

    @Test
    @DisplayName("ADR-0011: CommandExecutionException（不正遷移）は WARN スキップする")
    void CommandExecutionExceptionをスキップする() {
        doThrow(new CommandExecutionException("不正な状態遷移",
                new IllegalStateException("不正な状態遷移です: UNLOADED → DELIVERED")))
                .when(commandGateway).sendAndWait(any());

        // 例外が伝播せずスキップされる
        handler.on(event("CLAIM"));
    }

    @Test
    @DisplayName("ADR-0011 外: その他の例外は伝播させる（リトライに任せる）")
    void その他の例外は伝播させる() {
        doThrow(new RuntimeException("network down"))
                .when(commandGateway).sendAndWait(any());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> handler.on(event("RECEIVE")));
    }
}
