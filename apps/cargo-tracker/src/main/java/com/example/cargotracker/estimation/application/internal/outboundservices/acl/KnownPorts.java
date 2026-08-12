package com.example.cargotracker.estimation.application.internal.outboundservices.acl;

import java.util.Collection;
import java.util.List;

/**
 * 港マスタに登録されている港かを判定する出力ポート（U5。IT20）。
 *
 * <p><strong>読むだけである。</strong> 見積一覧が 0 件だったときに
 * 「条件に一致しない」のか「港コードを打ち間違えた」のかを分けるために使う
 * （{@code EstimateFilter.EmptyReason}）。
 *
 * <p><strong>Routing の同名ポートを参照しない。</strong> BC ごとにポートを持つのが
 * 本プロジェクトの規律であり（ADR-012）、共有すると Estimation の
 * アプリケーション層が Routing を知ることになる。
 */
public interface KnownPorts {

    /**
     * 港マスタに存在しない港コードを返す。
     *
     * <p><strong>1 件ずつ問い合わせない。</strong> 出発地・目的地を別々に引くと、
     * 一覧を開くたびに往復が 2 回になる。
     *
     * @param unlocodes 確かめたい UN/LOCODE
     * @return 存在しなかったもの（すべて存在すれば空）
     */
    List<String> findUnknown(Collection<String> unlocodes);
}
