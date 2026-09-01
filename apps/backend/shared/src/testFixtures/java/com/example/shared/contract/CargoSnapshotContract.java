package com.example.shared.contract;

import java.util.List;

/**
 * 追跡番号で貨物を引く REST の契約（US15-1・[ADR-023] 決定 2）。
 *
 * <p><strong>両側が同じ 1 つを読む。</strong>写しを 2 つ置くと、片方だけ直したことを誰も
 * 検出できない（IT7 返済枠 0.12 と同じ形）。
 *
 * <p>ここに置くのは<strong>契約であって実装ではない</strong>。DTO は BC ごとに持つ
 * （相手の型を持ち込まない）。共有するのは「両者が合意した経路と項目」だけである。
 */
public final class CargoSnapshotContract {

    private CargoSnapshotContract() {
    }

    /** 呼び出す経路。{@code {trackingNumber}} は追跡番号に置き換える。 */
    public static final String PATH = "/api/v1/bookings/by-tracking-number/{trackingNumber}";

    /**
     * 呼び出してよい主体（[ADR-019] 後日談 3）。
     *
     * <p>名乗らないと、相手の [ADR-007] フィルタが一律に断る。IT5 では名乗りを忘れ、
     * <strong>実環境の往復を通すまで誰も気づかなかった</strong>。
     */
    public static final String CALLER_PRINCIPAL = "system:handlingms";

    /** 流れる項目。順序も含めて契約である。 */
    public static final List<String> FIELDS =
            List.of("bookingId", "originUnLocode", "destinationUnLocode", "legs", "simulated");

    /** 旅程の区間の項目。 */
    public static final List<String> LEG_FIELDS =
            List.of("voyageNumber", "loadUnLocode", "unloadUnLocode");
}
