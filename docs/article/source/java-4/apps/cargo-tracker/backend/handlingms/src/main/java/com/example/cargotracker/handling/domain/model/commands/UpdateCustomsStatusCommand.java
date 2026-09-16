package com.example.cargotracker.handling.domain.model.commands;

import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 通関状態を更新する（UC21 / US29 §受入基準 2）。
 *
 * <p><b>理由は必須</b>（不変条件 2）。何が起きたか読めない記録を残さない——
 * 履歴はイベント列そのものなので、理由を落とすとどこにも残らない。</p>
 */
public record UpdateCustomsStatusCommand(
        @TargetEntityId String declarationNumber,
        CustomsStatus status,
        String reason,
        String changedBy) {
}
