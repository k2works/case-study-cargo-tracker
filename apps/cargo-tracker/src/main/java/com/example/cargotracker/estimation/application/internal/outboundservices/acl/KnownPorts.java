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
 *
 * <p><strong>ADR-021 の名簿（{@code CrossContextPortPolicyTest}）の対象外である。</strong>
 * 名簿が守っているのは「BC 越しに<strong>状態を変える</strong>同期ポートの失敗が人に届くか」
 * であり、これは読むだけの問い合わせなので「失敗の届け先」に書くことが無い。
 * <strong>黙って外さず、ここに書いて外す</strong>（{@code EntityEncapsulationTest} が
 * {@code reconstruct} について行っているのと同じ形）。
 *
 * <p><strong>UN/LOCODE を {@code String} で受ける</strong>のは、条件が画面から届いた
 * 生の文字列であり<strong>実在しない綴りを含みうる</strong>ためである。共有カーネルの
 * {@code Location} は形式（2 文字＋3 文字）を検証するため、
 * 「打ち間違いを見つける」というこのポートの目的と噛み合わない。
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
