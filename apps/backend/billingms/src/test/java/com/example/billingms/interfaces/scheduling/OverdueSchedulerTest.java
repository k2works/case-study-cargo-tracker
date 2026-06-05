package com.example.billingms.interfaces.scheduling;

import com.example.billingms.application.InvoiceQueryService;
import com.example.billingms.domain.commands.MarkOverdueCommand;
import com.example.billingms.domain.projections.InvoiceSummary;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link OverdueScheduler} の単体テスト（US23、IT7 T4.6）。
 *
 * <p>督促候補を抽出して MarkOverdueCommand を発火する基本フロー、および
 * 個別 invoice の失敗時に他の処理を継続する独立性を検証する。</p>
 */
class OverdueSchedulerTest {

    private InvoiceQueryService queryService;
    private CommandGateway commandGateway;
    private OverdueScheduler scheduler;

    @BeforeEach
    void setUp() {
        queryService = mock(InvoiceQueryService.class);
        commandGateway = mock(CommandGateway.class);
        scheduler = new OverdueScheduler(queryService, commandGateway);
    }

    private InvoiceSummary summary(String invoiceId) {
        InvoiceSummary s = new InvoiceSummary();
        s.setInvoiceId(invoiceId);
        return s;
    }

    @Test
    @DisplayName("US23 T4.6: 督促候補がない場合は発火 0 件")
    void 候補なし() {
        when(queryService.findOverdueCandidates()).thenReturn(List.of());

        int fired = scheduler.runOverdueDetection();

        assertThat(fired).isZero();
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("US23 T4.6: 督促候補 3 件すべてに MarkOverdueCommand を発火")
    void 候補3件全件発火() {
        when(queryService.findOverdueCandidates()).thenReturn(List.of(
                summary("INV-001"), summary("INV-002"), summary("INV-003")));

        int fired = scheduler.runOverdueDetection();

        assertThat(fired).isEqualTo(3);
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-001"));
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-002"));
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-003"));
    }

    @Test
    @DisplayName("US23 T4.6: 1 件の AggregateNotFoundException でも残り 2 件を継続発火")
    void 一件失敗でも残り継続() {
        when(queryService.findOverdueCandidates()).thenReturn(List.of(
                summary("INV-A"), summary("INV-B"), summary("INV-C")));
        when(commandGateway.sendAndWait(new MarkOverdueCommand("INV-B")))
                .thenThrow(new AggregateNotFoundException("INV-B", "not found"));

        int fired = scheduler.runOverdueDetection();

        assertThat(fired).isEqualTo(2);
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-A"));
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-B"));
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-C"));
    }

    @Test
    @DisplayName("US23 T4.6: CommandExecutionException でもスキップして継続")
    void コマンド実行失敗でもスキップ() {
        when(queryService.findOverdueCandidates()).thenReturn(List.of(
                summary("INV-X"), summary("INV-Y")));
        when(commandGateway.sendAndWait(new MarkOverdueCommand("INV-X")))
                .thenThrow(new CommandExecutionException("既に OVERDUE...", null));

        int fired = scheduler.runOverdueDetection();

        assertThat(fired).isEqualTo(1);
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-Y"));
    }
}
