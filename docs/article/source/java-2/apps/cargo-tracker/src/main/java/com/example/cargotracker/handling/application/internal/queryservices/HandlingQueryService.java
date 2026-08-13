package com.example.cargotracker.handling.application.internal.queryservices;

import java.util.List;

/** 荷役作業の読み取り（US15）。 */
public interface HandlingQueryService {

    /**
     * 直近の荷役作業を新しい順で返す。
     *
     * <p><strong>登録した作業が先頭に出る</strong>（{@code ui_design.md}）。
     * 自分が今スキャンした荷物を探し直させない。
     *
     * @param limit 取得件数の上限
     */
    List<HandlingActivityView> findRecent(int limit);

    /**
     * まだ降ろしていない荷降し手配（US30）。
     *
     * <p><strong>「手配した」を現場が読める場所に置く。</strong> 輸送中のキャンセルを
     * 承認すると陸揚げ地が決まるが、それが荷役の一覧に出ないかぎり、
     * <strong>船から降ろす人には何も届いていない</strong>。
     *
     * <p><strong>荷降し（{@code UNLOAD}）を記録した手配は返さない。</strong>
     * 済んだ指示が残り続けると、現場は毎朝それを読み飛ばすようになり、
     * やがて新しい指示も読み飛ばす。
     *
     * <p>並びは<strong>承認の古い順</strong>。待たせている手配から捌く。
     */
    List<DischargeOrderView> findPendingDischarges();
}
