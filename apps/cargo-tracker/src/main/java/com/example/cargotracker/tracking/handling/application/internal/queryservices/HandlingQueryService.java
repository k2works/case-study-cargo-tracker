package com.example.cargotracker.tracking.handling.application.internal.queryservices;

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
}
