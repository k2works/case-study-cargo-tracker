package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.handling.application.internal.outboundservices.acl.ApprovedCancellations;
import com.example.cargotracker.handling.application.internal.queryservices
        .DischargeOrderSelection;
import com.example.cargotracker.handling.application.internal.queryservices.DischargeOrderView;
import com.example.cargotracker.handling.application.internal.queryservices.HandlingActivityView;
import com.example.cargotracker.handling.application.internal.queryservices.HandlingQueryService;
import com.example.cargotracker.handling.domain.model.HandlingType;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link HandlingQueryService} の MyBatis 実装（CQRS の読み取り側）。 */
@Service
public class MyBatisHandlingQueryService implements HandlingQueryService {

    private final HandlingMapper mapper;
    private final ApprovedCancellations approvedCancellations;

    /** **業務のタイムゾーンで「今日」を決める。** UTC で数えると境目がずれる */
    private final java.time.Clock clock;

    public MyBatisHandlingQueryService(
            HandlingMapper mapper,
            ApprovedCancellations approvedCancellations,
            java.time.Clock clock) {
        this.mapper = mapper;
        this.approvedCancellations = approvedCancellations;
        this.clock = clock;
    }

    @Override
    public List<HandlingActivityView> findRecent(int limit) {
        return mapper.findRecent(limit).stream()
                .map(MyBatisHandlingQueryService::toView)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>どれが残るかは {@link DischargeOrderSelection} が決める</strong>
     * （IT17 の R3）。ここが決めるのは問い合わせ方だけである。
     *
     * <p><strong>問い合わせは 2 回で済ませる。</strong> 手配をまとめて受け取り、
     * 済んだ予約もまとめて引く。1 件ごとに「降ろしたか」を尋ねると、
     * <strong>待ち行列が伸びるほど遅くなる</strong>（IT13 の C4 / IT15 の P3）。
     */
    @Override
    public List<DischargeOrderView> findPendingDischarges() {
        List<ApprovedCancellations.DischargeOrder> orders =
                approvedCancellations.findApprovedDischarges();
        List<UUID> bookingIds = DischargeOrderSelection.bookingIds(orders);
        if (bookingIds.isEmpty()) {
            // **空で問い合わせない。** IN () は SQL として成り立たない
            return List.of();
        }
        // **どれが残るかは規則が決める**（IT17 の R3）。ここが決めるのは
        // 「何回・どの SQL で引くか」だけである
        Set<UUID> unloaded = new HashSet<>(mapper.findUnloadedBookingIds(bookingIds));
        return DischargeOrderSelection.pending(orders, unloaded).stream()
                .map(this::toDischargeOrderView)
                .toList();
    }

    /**
     * 荷降し手配を表示用に変換する。
     *
     * <p><strong>貨物種別は現物に触る人のために運ぶ</strong>（US05）。危険物・冷凍なら
     * 降ろす準備が変わる。荷役の履歴側には出ているのに、<strong>先に読む手配側に
     * 出ていないと、申告を登録した意味が半分になる</strong>。
     */
    private DischargeOrderView toDischargeOrderView(
            ApprovedCancellations.DischargeOrder order) {
        return new DischargeOrderView(
                order.trackingNumber(), order.dischargeUnlocode(),
                order.dischargeName(), order.decidedAt(),
                specialHandlingLabel(order.cargoType()),
                waitingDays(order.decidedAt()));
    }

    /**
     * 承認から待っている日数（IT17 の R1）。
     *
     * <p><strong>業務のタイムゾーンで数える。</strong> UTC で数えると、時差の分だけ
     * 「本日」の境目がずれる。
     */
    private long waitingDays(Instant decidedAt) {
        return java.time.temporal.ChronoUnit.DAYS.between(
                decidedAt.atZone(clock.getZone()).toLocalDate(),
                java.time.LocalDate.now(clock));
    }

    private static HandlingActivityView toView(HandlingActivityRecord row) {
        return new HandlingActivityView(
                row.getId(),
                new HandlingActivityView.Work(
                        row.getEventCompletionTime(),
                        // **日本語ラベルの正典は列挙型が持つ。** 画面に対応表を書き写さない
                        HandlingType.valueOf(row.getEventType()).displayName(),
                        row.getLocationUnlocode(),
                        row.getVoyageNumber() == null ? "" : row.getVoyageNumber(),
                        row.getOperatorName() == null ? "" : row.getOperatorName(),
                        row.getNote() == null ? "" : row.getNote()),
                new HandlingActivityView.CargoSummary(
                        // IT6 以前の記録は番号を持たない（V13 で追加した列）
                        row.getTrackingNumber() == null ? "" : row.getTrackingNumber(),
                        row.getBookingId().toString(),
                        // **引き渡しの証明は残すだけでなく読めなければ意味がない**（レビュー H3）
                        row.getClaimConsigneeName() == null ? "" : row.getClaimConsigneeName(),
                        specialHandlingLabel(row.getCargoType())),
                // **取り消しで戻る状態を持つのは引取だけである**（US36）
                HandlingType.CLAIM.name().equals(row.getEventType()),
                row.getCancelledAt() != null);
    }

    /**
     * 特別な取り扱いの表示名（US05）。
     *
     * <p><strong>一般貨物では空にする。</strong> すべての行にバッジが付くと、
     * 気をつけるべき行が埋もれる。
     */
    private static String specialHandlingLabel(String cargoType) {
        if (cargoType == null) {
            return "";
        }
        return switch (cargoType) {
            case "HAZARDOUS" -> "危険物";
            case "REFRIGERATED" -> "冷凍・冷蔵";
            default -> "";
        };
    }
}
