package com.example.billingms.interfaces.scheduling;

import com.example.billingms.application.InvoiceQueryService;
import com.example.billingms.domain.commands.MarkOverdueCommand;
import com.example.billingms.domain.projections.InvoiceSummary;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 督促スケジューラ（US23、IT7 T4.6）。
 *
 * <p>毎日 09:00 JST に {@code billing_status = INVOICED AND payment_due < now()} を全件抽出して
 * 順次 {@link MarkOverdueCommand} を発火する。{@code InvoiceOverdueEvent} 経由で
 * {@code notifyOverdue} が呼ばれる（T4.4）。</p>
 *
 * <p>billingms 単一 instance 前提（IT7 制約）。IT8 で ShedLock 等のクラスタ排他制御を追加予定。
 * {@code @ProcessingGroup} は付与せず、{@link Scheduled} 駆動の通常 Spring Bean。</p>
 *
 * <p>テストでは {@link #runOverdueDetection()} を直接呼んでスケジューラに依存せず検証する。</p>
 */
@Component
public class OverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueScheduler.class);

    private final InvoiceQueryService queryService;
    private final CommandGateway commandGateway;

    public OverdueScheduler(InvoiceQueryService queryService, CommandGateway commandGateway) {
        this.queryService = queryService;
        this.commandGateway = commandGateway;
    }

    /**
     * 毎日 09:00 JST 実行。{@code cron = "0 0 9 * * *"}（{@code zone} は JVM 既定）。
     */
    @Scheduled(cron = "${billing.overdue.cron:0 0 9 * * *}", zone = "Asia/Tokyo")
    public void scheduledRun() {
        runOverdueDetection();
    }

    /**
     * 督促対象の抽出 → {@link MarkOverdueCommand} 発火を実行する。
     *
     * <p>各 invoiceId 単位の例外は WARN ログでスキップし、後続の発火を継続する
     * （1 件の失敗が全件失敗に波及しないよう独立処理）。</p>
     *
     * @return 発火件数（テスト検証用）
     */
    public int runOverdueDetection() {
        List<InvoiceSummary> candidates = queryService.findOverdueCandidates();
        int fired = 0;
        for (InvoiceSummary candidate : candidates) {
            try {
                commandGateway.sendAndWait(new MarkOverdueCommand(candidate.getInvoiceId()));
                fired++;
            } catch (AggregateNotFoundException ex) {
                log.warn("[OverdueScheduler] 対象集約が存在しません invoiceId={}", candidate.getInvoiceId());
            } catch (CommandExecutionException ex) {
                log.warn("[OverdueScheduler] MarkOverdueCommand をスキップしました invoiceId={}: {}",
                        candidate.getInvoiceId(), ex.getMessage());
            }
        }
        log.info("[OverdueScheduler] 督促判定完了 候補={} 発火={}", candidates.size(), fired);
        return fired;
    }
}
