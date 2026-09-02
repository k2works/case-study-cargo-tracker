package com.example.handlingms.application.internal.outboundservices.acl;

import com.example.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.handlingms.domain.model.valueobjects.HandlingTrackingNumber;
import java.util.Optional;

/**
 * 追跡番号から貨物を引く（US15-1・[ADR-023] 決定 2）。
 *
 * <p>出力ポート。<strong>相手が bookingms であることも、REST であることも、ここには現れない</strong>。
 * ドメインとユースケースは「追跡番号で貨物を引く」ことだけを知る。
 */
public interface CargoSnapshotFinder {

    /**
     * 追跡番号で引く。見つからなければ空を返す。
     *
     * @throws CargoLookupUnavailableException 相手に確かめられなかったとき。
     *     <strong>「確かめられなかった」と「無かった」を混ぜない</strong>——混ぜると、
     *     bookingms が落ちているときに荷役作業員へ「その追跡番号は存在しません」と伝わり、
     *     作業員は番号を疑って打ち直し続ける
     */
    Optional<CargoSnapshot> findByTrackingNumber(HandlingTrackingNumber trackingNumber);
}
