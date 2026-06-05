package com.example.handlingms.domain.services;

import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.projections.CargoSnapshot;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 荷役登録時の集約越境バリデーションを担うドメインサービス（US15 / IT5 3.2）。
 *
 * <p>{@code HandlingActivity} 集約は Event Sourcing で自己完結する一方、以下の不変条件は
 * 集約境界を越えた状態を参照する必要があるため、本サービスが Read Model 経由で判定する。</p>
 *
 * <ul>
 *   <li>重複登録拒否: 同一 trackingNumber + handlingType + unlocode + 5 分粒度の occurredAt
 *       が既に登録されていないか（domain-model.md / data-model.md UNIQUE 制約相当）</li>
 *   <li>予定外検知: {@code CargoSnapshot} ACL の origin / destination と
 *       実発生場所が一致するかを判定し、{@code UnexpectedHandlingDetectedEvent} の発行根拠を返す</li>
 * </ul>
 *
 * <p>Aggregate からは {@code @CommandHandler} のパラメータとして注入する。</p>
 */
@Component
public class HandlingValidationService {

    /** 重複判定の時間粒度（前後 5 分） */
    static final long DUPLICATE_WINDOW_MINUTES = 5;

    private final HandlingValidationRepository repository;

    public HandlingValidationService(HandlingValidationRepository repository) {
        this.repository = repository;
    }

    /**
     * 重複登録（同一 trackingNumber + handlingType + unlocode + 5 分粒度）の有無を返す。
     * window は occurredAt の ±5 分の範囲。
     */
    public boolean hasDuplicate(String trackingNumber, HandlingType handlingType,
                                String unlocode, LocalDateTime occurredAt) {
        LocalDateTime windowStart = occurredAt.minusMinutes(DUPLICATE_WINDOW_MINUTES);
        LocalDateTime windowEnd = occurredAt.plusMinutes(DUPLICATE_WINDOW_MINUTES);
        long count = repository.countDuplicates(
                trackingNumber, handlingType, unlocode, windowStart, windowEnd);
        return count > 0;
    }

    /**
     * 予定外検知。CargoSnapshot ACL の予定と実発生場所を比較する。
     *
     * <ul>
     *   <li>RECEIVE: snapshot.originUnlocode と一致しなければ予定外</li>
     *   <li>CLAIM: snapshot.destinationUnlocode と一致しなければ予定外</li>
     *   <li>LOAD / UNLOAD / CUSTOMS: 旅程の詳細は本 IT で投影されていないため判定保留（false）</li>
     *   <li>CargoSnapshot 自体が未到着の場合: 判定保留（false、cross-service 順序の問題で
     *       後続イベントで CargoSnapshot が到着し得る）</li>
     * </ul>
     *
     * @return 予定外と判断した場合の理由（{@link Optional#empty()} なら予定通り）
     */
    public Optional<String> detectUnexpected(String trackingNumber, HandlingType handlingType,
                                             String unlocode) {
        CargoSnapshot snapshot = repository.findCargoSnapshotByTrackingNumber(trackingNumber);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (handlingType == HandlingType.RECEIVE
                && !unlocode.equals(snapshot.getOriginUnlocode())) {
            return Optional.of("RECEIVE が origin (" + snapshot.getOriginUnlocode()
                    + ") と異なる場所 (" + unlocode + ") で発生");
        }
        if (handlingType == HandlingType.CLAIM
                && !unlocode.equals(snapshot.getDestinationUnlocode())) {
            return Optional.of("CLAIM が destination (" + snapshot.getDestinationUnlocode()
                    + ") と異なる場所 (" + unlocode + ") で発生");
        }
        return Optional.empty();
    }
}
