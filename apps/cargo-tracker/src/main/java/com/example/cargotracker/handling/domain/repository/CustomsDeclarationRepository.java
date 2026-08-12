package com.example.cargotracker.handling.domain.repository;

import com.example.cargotracker.handling.domain.model.aggregates.CustomsDeclaration;
import java.util.List;
import java.util.Optional;

/**
 * 通関申告の出力ポート（US29）。実装はインフラ層に置く（DIP）。
 *
 * <p><strong>申告は荷役作業に紐づく。</strong> 「どの荷役でどの貨物を通したか」の
 * 記録であり、貨物に紐づかない申告は業務上あり得ない。
 */
public interface CustomsDeclarationRepository {

    /**
     * 通関の荷役作業に紐づけて申告を保存する。
     *
     * <p>変更履歴も同じトランザクションで積む。
     * <strong>履歴だけが落ちると「なぜ止めたのか」が消える。</strong>
     *
     * @param handlingActivityId 通関（CUSTOMS）の荷役作業 ID
     * @return 保存できたか（楽観的な競合で 0 行なら false）
     */
    boolean save(long handlingActivityId, CustomsDeclaration declaration);

    /** 追跡番号から申告を引く。**1 貨物に 1 件の申告を前提とする。** */
    Optional<CustomsDeclaration> findByTrackingNumber(String trackingNumber);

    Optional<CustomsDeclaration> findById(long declarationId);

    /** 追跡番号に対応する通関（CUSTOMS）の荷役作業 ID。無ければ空。 */
    Optional<Long> findCustomsHandlingId(String trackingNumber);

    /** 申告の追跡番号（通知・例外の起票に使う）。 */
    Optional<String> findTrackingNumber(long declarationId);

    /** 変更履歴を古い順で返す（申告詳細）。 */
    List<com.example.cargotracker.handling.domain.model.entities.CustomsStatusChange> findHistory(
            long declarationId);
}
