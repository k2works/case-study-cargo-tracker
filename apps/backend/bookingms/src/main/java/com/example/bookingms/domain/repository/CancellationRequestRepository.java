package com.example.bookingms.domain.repository;

import com.example.bookingms.domain.model.aggregates.CancellationRequest;
import java.util.List;
import java.util.Optional;

/** キャンセル申請の保存先（出力ポート）。 */
public interface CancellationRequestRepository {

    /**
     * 新しい申請を保存する。
     *
     * <p><strong>更新とは別のメソッドにする。</strong>「常に INSERT する save」で更新まで
     * 賄うと、最初の承認のときに行が増える。
     */
    CancellationRequest save(CancellationRequest request);

    /** 承認・却下の結果を書き込む。 */
    CancellationRequest updateDecision(CancellationRequest request);

    /**
     * その貨物の判断待ちの申請。
     *
     * <p><strong>高々 1 件である。</strong>申請側がこれで 2 通目を断るため、
     * 「最新の 1 件」を暗黙に選ぶ必要がない。
     */
    Optional<CancellationRequest> findAwaitingByCargoId(Long cargoId);

    /** その貨物の最新の申請。画面が「いまどうなっているか」を出すために引く。 */
    Optional<CancellationRequest> findLatestByCargoId(Long cargoId);

    /**
     * その貨物のキャンセル申請を<strong>すべて</strong>（新しい順・US30-10）。
     *
     * <p><strong>却下されて再申請した予約では、前回の却下理由が要る。</strong>
     * 最新の 1 件だけを返すと、「なぜ一度断られたか」が予約詳細から消える。
     */
    List<CancellationRequest> findAllByCargoId(Long cargoId);

    /** 承認待ちの一覧（US30-4）。**古い順**——放っておくほど貨物は目的地へ近づく。 */
    List<CancellationRequest> findAwaitingDecision(int limit);

    /**
     * <strong>陸揚げ待ち</strong>——承認済みで陸揚げ地が決まっている申請（IT10 返済枠 0.3）。
     *
     * <p>荷役の担当者が「どの貨物をどこで降ろすことになったか」を自分で知るための入口。
     * <strong>連絡を忘れると、貨物は指定した港を通り過ぎる。</strong>
     */
    List<CancellationRequest> findAwaitingDischarge(int limit);
}
