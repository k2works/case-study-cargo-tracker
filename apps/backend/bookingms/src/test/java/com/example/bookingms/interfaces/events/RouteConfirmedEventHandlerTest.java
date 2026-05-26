package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.commands.AssignRouteToCargoCommand;
import com.example.shared.events.RouteConfirmedEvent;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 経路確定イベント受信ハンドラ（US11 / cross-service、ADR-0009）のユニットテスト。
 */
@ExtendWith(MockitoExtension.class)
class RouteConfirmedEventHandlerTest {

    @Mock
    private CommandGateway commandGateway;

    @InjectMocks
    private RouteConfirmedEventHandler handler;

    @Test
    void 経路確定イベントを受信すると経路割当コマンドを発行する() {
        RouteConfirmedEvent event = new RouteConfirmedEvent("B-001", List.of(
                new RouteConfirmedEvent.LegData("V-A", "JPTYO", "SGSIN",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0)),
                new RouteConfirmedEvent.LegData("V-B", "SGSIN", "DEHAM",
                        LocalDateTime.of(2026, 7, 12, 9, 0), LocalDateTime.of(2026, 7, 30, 18, 0))));

        handler.on(event);

        ArgumentCaptor<AssignRouteToCargoCommand> captor =
                ArgumentCaptor.forClass(AssignRouteToCargoCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        AssignRouteToCargoCommand command = captor.getValue();
        assertThat(command.bookingId()).isEqualTo("B-001");
        assertThat(command.legs()).hasSize(2);
        assertThat(command.legs().get(0).voyageNumber()).isEqualTo("V-A");
        assertThat(command.legs().get(0).loadUnlocode()).isEqualTo("JPTYO");
        assertThat(command.legs().get(0).unloadUnlocode()).isEqualTo("SGSIN");
        assertThat(command.legs().get(1).voyageNumber()).isEqualTo("V-B");
        assertThat(command.legs().get(1).unloadUnlocode()).isEqualTo("DEHAM");
    }

    @Test
    void コマンド実行が例外でも冪等にスキップして伝播しない() {
        RouteConfirmedEvent event = new RouteConfirmedEvent("B-002", List.of(
                new RouteConfirmedEvent.LegData("V-DIRECT", "JPTYO", "USNYC",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0))));
        // 重複配信・tracking 再処理で既に経路提案中の場合などに発生する CommandExecutionException
        when(commandGateway.sendAndWait(any(AssignRouteToCargoCommand.class)))
                .thenThrow(new CommandExecutionException("経路を割り当てできるのは経路設計中の予約のみです", null));

        // 例外を握りつぶして処理を継続する（tracking プロセッサがブロックしない）
        assertThatCode(() -> handler.on(event)).doesNotThrowAnyException();
    }

    @Test
    void 対象予約が存在しない場合は冪等にスキップして伝播しない() {
        RouteConfirmedEvent event = new RouteConfirmedEvent("BK-RC-1779779588376", List.of(
                new RouteConfirmedEvent.LegData("V-DIRECT", "JPTYO", "USNYC",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0))));
        // event store がリセットされた環境での再生イベントや、対象予約未作成の古いイベントでは
        // 集約をロードできず AggregateNotFoundException が送出される（CommandExecutionException とは別系統）
        when(commandGateway.sendAndWait(any(AssignRouteToCargoCommand.class)))
                .thenThrow(new AggregateNotFoundException(
                        "BK-RC-1779779588376", "The aggregate was not found in the event store"));

        // ERROR で再スローせず冪等にスキップする（tracking プロセッサがブロック・ログ汚染しない）
        assertThatCode(() -> handler.on(event)).doesNotThrowAnyException();
    }

    @Test
    void 冪等スキップ対象外の例外は握り潰さず伝播する() {
        RouteConfirmedEvent event = new RouteConfirmedEvent("B-003", List.of(
                new RouteConfirmedEvent.LegData("V-X", "JPTYO", "USNYC",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0))));
        // 冪等スキップ対象（AggregateNotFoundException / CommandExecutionException）以外の真のエラーは
        // 握り潰さず伝播させ、tracking プロセッサのエラーハンドラ（再試行・可視化）に委ねる。
        // catch 範囲を不用意に広げる回帰を防ぐためのネガティブテスト（レビュー H1）。
        when(commandGateway.sendAndWait(any(AssignRouteToCargoCommand.class)))
                .thenThrow(new RuntimeException("予期しない基盤エラー"));

        assertThatThrownBy(() -> handler.on(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("予期しない基盤エラー");
    }
}
