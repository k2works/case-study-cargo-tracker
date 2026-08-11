package com.example.cargotracker.estimation.application.internal.commandservices;

import com.example.cargotracker.estimation.application.internal.outboundservices.acl
        .RouteCandidateSource;
import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import com.example.cargotracker.estimation.domain.model.EstimationCargoType;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.estimation.domain.repository.EstimateRepository;
import com.example.cargotracker.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * 見積を作る（US01）。
 *
 * <p>ルート候補は本サービスが Routing へ問い合わせて付与する（ADR-023。IT18 タスク 3）。
 */
@Service
public class CreateEstimateCommandService {

    private final EstimateRepository repository;
    private final RouteCandidateSource routeCandidates;

    /** <strong>「今日」は業務のタイムゾーンで決める。</strong> */
    private final Clock clock;

    public CreateEstimateCommandService(
            EstimateRepository repository,
            RouteCandidateSource routeCandidates,
            Clock clock) {
        this.repository = repository;
        this.routeCandidates = routeCandidates;
        this.clock = clock;
    }

    /**
     * 見積を作る。
     *
     * @return 受け付けたなら見積 ID、拒んだなら理由
     */
    public Result create(Request request) {
        LocalDate today = LocalDate.now(clock);
        if (request.arrivalDeadline().isBefore(today)) {
            // **過ぎた期限の見積は、作った瞬間に期限切れである**（ui_design.md）
            return Result.rejected("希望到着期限は当日以降を指定してください");
        }
        Estimate estimate;
        try {
            estimate = Estimate.create(
                    Location.of(request.origin()),
                    Location.of(request.destination()),
                    request.arrivalDeadline(),
                    request.cargoType(),
                    request.weightKg());
        } catch (IllegalArgumentException e) {
            // **catch は生成だけを囲む**（IT15 の P2）。保存まで囲むと原因が消える
            return Result.rejected(e.getMessage());
        }
        // **候補は Routing の探索から作る**（ADR-023）。固定値にすると、
        // 画面は動くのに数字が現実と無関係になる
        var found = routeCandidates.findCandidates(new RouteCandidateSource.Query(
                request.origin(), request.destination(), request.arrivalDeadline(),
                request.cargoType().name(), request.weightKg()));
        estimate.replaceCandidates(
                found.candidates().stream()
                        .map(c -> new RouteCandidate(
                                c.voyageNumber(), c.transitPort(), c.transitDays(),
                                c.estimatedCost(), c.currency()))
                        .toList(),
                reasonOf(found));
        repository.save(estimate);
        return Result.accepted(estimate.estimateId());
    }

    /**
     * 候補が 0 件だった理由（受入基準 5。ADR-023）。
     *
     * <p><strong>「便が無い」と「期限に間に合わない」を混ぜない。</strong>
     */
    private static com.example.cargotracker.estimation.domain.model.NoCandidateReason reasonOf(
            RouteCandidateSource.Candidates found) {
        if (!found.isEmpty()) {
            return null;
        }
        return found.noVoyage()
                ? com.example.cargotracker.estimation.domain.model.NoCandidateReason.NO_VOYAGE
                : com.example.cargotracker.estimation.domain.model.NoCandidateReason.DEADLINE;
    }

    /**
     * 見積の依頼。
     *
     * @param origin          出発地 UN/LOCODE
     * @param destination     目的地 UN/LOCODE
     * @param arrivalDeadline 希望到着期限
     * @param cargoType       貨物種別
     * @param weightKg        重量（kg）
     */
    public record Request(
            String origin,
            String destination,
            LocalDate arrivalDeadline,
            EstimationCargoType cargoType,
            BigDecimal weightKg) { }

    /**
     * 結果。
     *
     * @param estimateId 受け付けたときの見積 ID
     * @param reason     拒んだときの理由
     */
    public record Result(EstimateId estimateId, String reason) {

        static Result accepted(EstimateId estimateId) {
            return new Result(estimateId, null);
        }

        static Result rejected(String reason) {
            return new Result(null, reason);
        }

        /** 受け付けたか。 */
        public boolean isAccepted() {
            return estimateId != null;
        }
    }
}
