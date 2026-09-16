package com.example.cargotracker.handling.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 荷役の記録を取り消す（UC13 / 不変条件 7）。
 *
 * <p><b>理由は必須。</b> 取り消しは現場で起きたことを打ち消す操作なので、
 * あとから見て「なぜ消えているのか」が分からないと突き合わせられない。</p>
 */
public record VoidHandlingActivityCommand(
        @TargetEntityId String activityId,
        String reason,
        String voidedBy) {
}
