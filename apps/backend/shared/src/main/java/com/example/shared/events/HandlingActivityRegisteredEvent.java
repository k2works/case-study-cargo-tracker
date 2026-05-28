package com.example.shared.events;

import java.time.LocalDateTime;

/**
 * 荷役作業登録イベント（US15・US16、cross-service）。
 *
 * <p>handlingms の {@code HandlingActivity} 集約が荷役作業（受領・積込・荷降し・引取・税関通過）を
 * 記録したときに発行し、trackingms が Kafka 経由で購読する（ADR-0009）。trackingms は本イベントから
 * {@code UpdateTransportStatusCommand} を発行して {@code TrackingActivity} の輸送状態を更新する（IT5 3.x）。</p>
 *
 * <p>cross-service の安定契約として shared モジュールに配置し、handlingms / trackingms が同一 FQCN で
 * シリアライズ・デシリアライズできるようにする。trackingms が状態遷移を自己完結で実施できるよう、追跡番号と
 * 作業種別・日時・場所・航海番号を保持する。引取（CLAIM）時の荷受人確認は {@link ClaimVerificationData}
 * として併載する。</p>
 *
 * <p>{@code voyageNumber} は LOAD / UNLOAD では必須（domain-model.md 不変条件）、それ以外は null 可。
 * {@code claimVerification} は CLAIM では必須、それ以外は null。
 * {@code unexpected} は予定外の場所・種別を {@code cargoSnapshot.isExpectedHandling} が検知した場合に true
 * （記録は許容、warning イベントとして扱う）。</p>
 *
 * <p>受信側ハンドラは ADR-0011（ホワイトリスト方式）に従い、
 * {@code AggregateNotFoundException} / {@code CommandExecutionException} の 2 種のみ WARN スキップし、
 * それ以外の例外は伝播させる。</p>
 */
public record HandlingActivityRegisteredEvent(
        String activityId,
        String trackingNumber,
        String handlingType,
        LocalDateTime occurredAt,
        String unlocode,
        String voyageNumber,
        String handlerId,
        ClaimVerificationData claimVerification,
        boolean unexpected
) {
    /**
     * 引取時（{@code handlingType = CLAIM}）の荷受人確認情報。
     * 署名参照（{@code signatureRef}）または確認コード（{@code confirmationCode}）のいずれかが必須
     * （domain-model.md HandlingActivity 不変条件・data-model.md claim_verification CHECK 制約）。
     */
    public record ClaimVerificationData(
            String consigneeName,
            String signatureRef,
            String confirmationCode,
            LocalDateTime verifiedAt
    ) {
    }
}
