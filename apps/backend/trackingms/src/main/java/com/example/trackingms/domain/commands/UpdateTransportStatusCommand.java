package com.example.trackingms.domain.commands;

import com.example.trackingms.domain.model.TransportStatus;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.LocalDateTime;

/**
 * 貨物状態手動更新コマンド（US17 / IT5 タスク 2.2）。
 *
 * <p>追跡管理者が {@link TransportStatus}（9 値）の状態・場所・日時を手動で更新する。
 * 状態遷移は {@code TransportStatusTransition.canTransition} が許可するもののみ受理し、
 * 不正遷移は {@code IllegalStateException} で拒否される。MISROUTED への遷移時は
 * {@code CargoMisroutedEvent} も同時に発行される（domain-model.md / user_story.md US17）。</p>
 *
 * @param trackingNumber 追跡番号（集約識別子）
 * @param toStatus       遷移先の輸送状態（必須）
 * @param unlocode       現在の港湾コード（任意。MISROUTED 検知時の発見場所等）
 * @param voyageNumber   関連する航海番号（任意。LOAD / UNLOAD / IN_TRANSIT で使用）
 * @param occurredAt     状態が変化した実時刻（必須。tracking_event の occurred_at に記録）
 * @param description    任意の説明（手動更新のメモ）
 */
public record UpdateTransportStatusCommand(
        @TargetAggregateIdentifier String trackingNumber,
        TransportStatus toStatus,
        String unlocode,
        String voyageNumber,
        LocalDateTime occurredAt,
        String description
) {
}
