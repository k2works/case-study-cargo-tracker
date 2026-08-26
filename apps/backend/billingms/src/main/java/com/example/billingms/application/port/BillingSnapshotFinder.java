package com.example.billingms.application.port;

import java.util.List;
import java.util.Optional;

/**
 * 料金算出の入力を引く出力ポート（[ADR-027] 決定 7）。
 *
 * <p>実装は bookingms を REST で呼ぶ ACL である。<strong>ポートが「何を頼むか」、実装が
 * 「どう呼ぶか」</strong>——HTTP か gRPC かがドメイン側の依存に現れないようにする。
 */
public interface BillingSnapshotFinder {

    /** 1 件を引く。料金算出の対象でなければ空を返す。 */
    Optional<BillableCargoSnapshot> findBillable(String bookingId);

    /** 対象をすべて並べる。**引取が終わった順**（相手が並べる）。 */
    List<BillableCargoSnapshot> findAllBillable();
}
