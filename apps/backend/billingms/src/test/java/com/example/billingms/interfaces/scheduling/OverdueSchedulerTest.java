package com.example.billingms.interfaces.scheduling;

import com.example.billingms.application.InvoiceQueryService;
import com.example.billingms.domain.commands.MarkOverdueCommand;
import com.example.billingms.domain.projections.InvoiceSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
 * {@link OverdueScheduler} の単体テスト（US23、IT7 T4.6 / review 中 Micrometer 化後）。
 *
 * <p>督促候補抽出 → MarkOverdueCommand 発火の基本フロー、個別 invoice の失敗時に他の処理を
 * 継続する独立性、および Micrometer counter（fired / skipped / candidates）が正しく
 * インクリメントされることを検証する。</p>
 */
class OverdueSchedulerTest {

    private InvoiceQueryService queryService;
    private CommandGateway commandGateway;
    private MeterRegistry registry;
    private OverdueScheduler scheduler;

    @BeforeEach
    void setUp() {
        queryService = mock(InvoiceQueryService.class);
        commandGateway = mock(CommandGateway.class);
        registry = new SimpleMeterRegistry();
        scheduler = new OverdueScheduler(queryService, commandGateway, registry);
    }

    private InvoiceSummary summary(String invoiceId) {
        InvoiceSummary s = new InvoiceSummary();
        s.setInvoiceId(invoiceId);
        return s;
    }

    private double counter(String name, String... tags) {
        return registry.find(name).tags(tags).counter() == null
                ? 0.0
                : registry.find(name).tags(tags).counter().count();
    }

    @Test
    @DisplayName("US23 T4.6: 督促候補がない場合は発火 0 件、candidates counter は増えない")
    void 候補なし() {
        when(queryService.findOverdueCandidates()).thenReturn(List.of());

        int fired = scheduler.runOverdueDetection();

        assertThat(fired).isZero();
        verify(commandGateway, never()).sendAndWait(any());
        assertThat(counter("billing.overdue.fired")).isZero();
        assertThat(counter("billing.overdue.candidates")).isZero();
    }

    @Test
    @DisplayName("US23 T4.6: 督促候補 3 件すべてに MarkOverdueCommand を発火 + counter 反映")
    void 候補3件全件発火() {
        when(queryService.findOverdueCandidates()).thenReturn(List.of(
                summary("INV-001"), summary("INV-002"), summary("INV-003")));

        int fired = scheduler.runOverdueDetection();

        assertThat(fired).isEqualTo(3);
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-001"));
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-002"));
        verify(commandGateway).sendAndWait(new MarkOverdueCommand("INV-003"));
        assertThat(counter("billing.overdue.fired")).isEqualTo(3.0);
        assertThat(counter("billing.overdue.candidates")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("US23 T4.6 review 中: AggregateNotFoundException でも残り継続 + skipped[reason=not_found] counter")
    void 一件失敗でも残り継続() {
        when(queryService.findOverdueCandidates()).thenReturn(List.of(
                summary("INV-A"), summary("INV-B"), summary("INV-C")));
        when(commandGateway.sendAndWait(new MarkOverdueCommand("INV-B")))
                .thenThrow(new AggregateNotFoundException("INV-B", "not found"));

        int fired = scheduler.runOverdueDetection();

        assertThat(fired).isEqualTo(2);
        assertThat(counter("billing.overdue.fired")).isEqualTo(2.0);
        assertThat(counter("billing.overdue.skipped", "reason", "not_found")).isEqualTo(1.0);
        assertThat(counter("billing.overdue.skipped", "reason", "exec_failure")).isZero();
    }

    @Test
    @DisplayName("US23 T4.6 review 中: CommandExecutionException で skipped[reason=exec_failure] counter")
    void コマンド実行失敗でもスキップ() {
        when(queryService.findOverdueCandidates()).thenReturn(List.of(
                summary("INV-X"), summary("INV-Y")));
        when(commandGateway.sendAndWait(new MarkOverdueCommand("INV-X")))
                .thenThrow(new CommandExecutionException("既に OVERDUE...", null));

        int fired = scheduler.runOverdueDetection();

        assertThat(fired).isEqualTo(1);
        assertThat(counter("billing.overdue.fired")).isEqualTo(1.0);
        assertThat(counter("billing.overdue.skipped", "reason", "exec_failure")).isEqualTo(1.0);
        assertThat(counter("billing.overdue.skipped", "reason", "not_found")).isZero();
    }
}
