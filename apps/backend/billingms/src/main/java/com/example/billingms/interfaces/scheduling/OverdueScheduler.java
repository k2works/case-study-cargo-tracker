package com.example.billingms.interfaces.scheduling;

import com.example.billingms.application.InvoiceQueryService;
import com.example.billingms.domain.commands.MarkOverdueCommand;
import com.example.billingms.domain.projections.InvoiceSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 督促スケジューラ（US23、IT7 T4.6 / review 中対応 Micrometer 化）。
 *
 * <p>毎日 09:00 JST に {@code billing_status = INVOICED AND payment_due < now()} を全件抽出して
 * 順次 {@link MarkOverdueCommand} を発火する。{@code InvoiceOverdueEvent} 経由で
 * {@code notifyOverdue} が呼ばれる（T4.4）。</p>
 *
 * <p>billingms 単一 instance 前提（IT7 制約）。IT8 で ShedLock 等のクラスタ排他制御を追加予定。
 * cron / zone は {@code billing.overdue.cron} / {@code billing.overdue.zone} で構成（IT7 review 中対応）。</p>
 *
 * <p>監視メトリクス（IT7 review 中対応、沈黙故障対策）:</p>
 * <ul>
 *   <li>{@code billing.overdue.fired}: MarkOverdueCommand が成功した件数</li>
 *   <li>{@code billing.overdue.skipped}: 例外でスキップした件数（タグ {@code reason=not_found|exec_failure}）</li>
 *   <li>{@code billing.overdue.candidates}: 督促候補件数（findOverdueCandidates の戻り値）</li>
 * </ul>
 */
@Component
public class OverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueScheduler.class);

    private final InvoiceQueryService queryService;
    private final CommandGateway commandGateway;
    private final Counter firedCounter;
    private final Counter notFoundCounter;
    private final Counter execFailureCounter;
    private final Counter candidatesCounter;

    public OverdueScheduler(InvoiceQueryService queryService,
                            CommandGateway commandGateway,
                            MeterRegistry registry) {
        this.queryService = queryService;
        this.commandGateway = commandGateway;
        this.firedCounter = Counter.builder("billing.overdue.fired")
                .description("OverdueScheduler が MarkOverdueCommand を成功発火した件数")
                .register(registry);
        this.notFoundCounter = Counter.builder("billing.overdue.skipped")
                .description("OverdueScheduler がスキップした件数（対象集約不在）")
                .tag("reason", "not_found")
                .register(registry);
        this.execFailureCounter = Counter.builder("billing.overdue.skipped")
                .description("OverdueScheduler がスキップした件数（コマンド実行失敗）")
                .tag("reason", "exec_failure")
                .register(registry);
        this.candidatesCounter = Counter.builder("billing.overdue.candidates")
                .description("findOverdueCandidates が返した督促候補件数")
                .register(registry);
    }

    /**
     * 定期実行（{@code @Scheduled} cron）+ ShedLock 分散排他（IT8 T2.2 / ADR-0017）。
     *
     * <p>{@code @SchedulerLock} で billingms multi-instance デプロイ時に 1 instance のみが
     * 処理することを保証する。lock 名 {@code billing-overdue-scheduler}（lock 種類が複数あれば
     * 別名を使う）。{@code lockAtMostFor = "PT19H"} は cron 周期（24h）の約 80%、
     * {@code lockAtLeastFor = "PT5H"} は短時間連続発火を防止する最小ロック保持時間。</p>
     */
    @Scheduled(cron = "${billing.overdue.cron}", zone = "${billing.overdue.zone}")
    @SchedulerLock(
            name = "billing-overdue-scheduler",
            lockAtMostFor = "PT19H",
            lockAtLeastFor = "PT5H"
    )
    public void scheduledRun() {
        runOverdueDetection();
    }

    /**
     * 督促対象の抽出 → {@link MarkOverdueCommand} 発火を実行する。
     *
     * <p>各 invoiceId 単位の例外は WARN ログ + Micrometer counter で記録し、後続の発火を継続する
     * （1 件の失敗が全件失敗に波及しないよう独立処理）。沈黙故障とならないよう
     * {@code billing.overdue.skipped} メトリクスで監視可能。</p>
     *
     * @return 発火件数（テスト検証用）
     */
    public int runOverdueDetection() {
        List<InvoiceSummary> candidates = queryService.findOverdueCandidates();
        candidatesCounter.increment(candidates.size());
        int fired = 0;
        for (InvoiceSummary candidate : candidates) {
            try {
                commandGateway.sendAndWait(new MarkOverdueCommand(candidate.getInvoiceId()));
                firedCounter.increment();
                fired++;
            } catch (AggregateNotFoundException ex) {
                notFoundCounter.increment();
                log.warn("[OverdueScheduler] 対象集約が存在しません invoiceId={}", candidate.getInvoiceId());
            } catch (CommandExecutionException ex) {
                execFailureCounter.increment();
                log.warn("[OverdueScheduler] MarkOverdueCommand をスキップしました invoiceId={}: {}",
                        candidate.getInvoiceId(), ex.getMessage());
            }
        }
        log.info("[OverdueScheduler] 督促判定完了 候補={} 発火={}", candidates.size(), fired);
        return fired;
    }
}
