package com.example.cargotracker.booking.domain.model.commands;

import java.time.LocalDate;
import java.util.List;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 経路の条件を調整して再算出できるようにする（UC08 / US10）。
 *
 * <p><b>調整を集約に記録する。</b> 画面の一時的な絞り込みにすると、誰がいつ期限を
 * 延ばしたかが残らない（UC08 の最低保証「調整条件と再算出結果が記録される」）。</p>
 *
 * <p><b>経路設計に入った予約は修正（US32・S24）が使えない</b>ので、期限を延ばす
 * 手段はこのコマンドだけである（{@code BookingStatus#canUpdateSpecification} は
 * 仮受付だけを許す）。二重の入口にはならない——S24 が開くのは仮受付のあいだ、
 * これが開くのは経路設計に入ってからで、同時には開かない。</p>
 *
 * <p><b>貨物種別は含めない。</b> 種別を変えるのは「その貨物が何か」を変えることで、
 * 危険物申告や温度条件が付いて回る。経路を探す条件ではないので US32 が持つ。</p>
 *
 * @param arrivalDeadline 新しい到着期限。変えないときは現在の値を送る
 * @param excludeUnLocodes 通らせたくない港。空なら制限なし
 * @param departFromUnLocode ここより後に出る便だけを候補にする。{@code null} なら制限なし
 */
public record AdjustRouteSpecificationCommand(
        @TargetEntityId String bookingId,
        LocalDate arrivalDeadline,
        List<String> excludeUnLocodes,
        String departFromUnLocode,
        String adjustedBy) {
}
