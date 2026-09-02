package com.example.handlingms.application.internal.commandservices;

import com.example.handlingms.domain.repository.CustomsDeclarationRepository;
import com.example.handlingms.application.internal.outboundservices.acl.CustomsStatusChanged;
import com.example.handlingms.application.internal.outboundservices.acl.HandlingEventNotifier;
import com.example.handlingms.domain.model.valueobjects.CargoBookingId;
import com.example.handlingms.domain.model.aggregates.CustomsDeclaration;
import com.example.handlingms.domain.model.valueobjects.CustomsStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

/**
 * 通関申告を読み、状態を更新する（US29-2・US29-6・US29-7・US29-8）。
 *
 * <p><strong>「今日」は注入した時計から決める</strong>（[ADR-010]）。UTC で判断すると、
 * 時差の分だけ督促が早まったり遅れたりする。テストも同じ時計を使う。
 */
@Service
public class ManageCustomsDeclarationUseCase {

    /** 一覧に出す上限。**朝の一覧としてこれ以上は読めない**。 */
    public static final int SEARCH_LIMIT = 200;

    /** 留置がこの日数を<strong>超えたら</strong>督促の対象（US29-6）。3 日ちょうどは対象外。 */
    public static final int HELD_OVERDUE_DAYS = 3;

    private final CustomsDeclarationRepository declarations;
    private final HandlingEventNotifier notifier;
    private final Clock clock;

    public ManageCustomsDeclarationUseCase(CustomsDeclarationRepository declarations,
            HandlingEventNotifier notifier, Clock clock) {
        this.declarations = declarations;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * 一覧・検索（US29-7）。
     *
     * <p>{@code unsettledOnly} は<strong>未決着（審査中・留置）だけ</strong>に絞る。
     * <strong>追跡管理者の朝の仕事は「未決着を上から片付ける」ことである。</strong>
     * 状態の絞り込みは単一選択のため、この 2 つを同時に見る手段が別に要る。
     *
     * <p><strong>総件数も返す。</strong>上限で切ったことを黙ると、担当者は
     * 「一覧に出ていないから無い」と読む。
     */
    public CustomsSearchResult search(String bookingId, String trackingNumber,
            String status, boolean unsettledOnly) {
        CustomsStatus parsed =
                status == null || status.isBlank() ? null : CustomsStatus.parse(status);
        List<CustomsDeclaration> found =
                declarations.search(bookingId, trackingNumber, parsed, unsettledOnly,
                        SEARCH_LIMIT);
        long total = declarations.count(bookingId, trackingNumber, parsed, unsettledOnly);
        return new CustomsSearchResult(found, total, SEARCH_LIMIT, total > SEARCH_LIMIT);
    }

    /**
     * 検索の結果。
     *
     * @param declarations 見つかった申告（上限まで）
     * @param totalCount 条件に合う総件数
     * @param limit 上限
     * @param truncated 上限で切られているか。<strong>黙って切ると「全件見た」と受け取られる</strong>
     */
    public record CustomsSearchResult(List<CustomsDeclaration> declarations, long totalCount,
            int limit, boolean truncated) {
    }

    /** 1 件を開く（US29-8）。 */
    public Optional<CustomsDeclaration> find(long declarationId) {
        return declarations.findById(declarationId);
    }

    /**
     * 状態を更新する（US29-2）。
     *
     * <p>判定は集約が持つ。ここで「理由が空か」を見比べると、規則がユースケースと
     * 集約の 2 か所に分かれる。
     */
    @Transactional
    public Optional<CustomsDeclaration> updateStatus(long declarationId, String status,
            String changedBy, String reason) {
        return declarations.findById(declarationId).map(declaration -> {
            CustomsStatus newStatus = CustomsStatus.parse(status);
            Instant changedAt = clock.instant();
            CustomsDeclaration updated = declarations.updateStatus(
                    declaration.updateStatus(newStatus, changedBy, reason, changedAt));

            // **留め置かれたことが誰の目にも入らないと、貨物はそのまま止まる**（US29-5）。
            // 例外の起票は trackingms が行う——追跡の状態を動かすのは追跡の仕事である
            notifier.customsStatusChanged(new CustomsStatusChanged(
                    updated.trackingNumber().value(), updated.cargoBookingId().value(),
                    updated.declarationNumber().value(), declaration.status().name(),
                    newStatus.name(), reason, changedBy, changedAt, clock.instant()));
            return updated;
        });
    }

    /**
     * 留置 3 日超の件数（US29-6）。<strong>件数から対象一覧へ辿れる</strong>（横断規約）。
     *
     * <p>判定は集約の述語をそのまま呼ぶ。ここで日数を数え直すと、督促の規則が 2 か所になる。
     */
    public long countHeldOverdue() {
        return declarations.search(null, null, CustomsStatus.HELD, false, SEARCH_LIMIT).stream()
                .filter(declaration -> declaration.isHeldOverdue(today(), zone(), HELD_OVERDUE_DAYS))
                .count();
    }

    /** 引取のガードが引く（US29-3）。**通関済の申告があるか**を見る。 */
    public Optional<CustomsDeclaration> latestFor(CargoBookingId cargoBookingId) {
        return declarations.findLatestByBookingId(cargoBookingId);
    }

    /** 業務上の今日。**呼び出し側が日付を作らない**——時計は 1 つに寄せる。 */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZoneId zone() {
        return clock.getZone();
    }
}
