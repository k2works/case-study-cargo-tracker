package com.example.shared.contract;

import java.util.List;

/**
 * trackingms が荷主境界を判定するために bookingms から引く Snapshot 契約（US33）。
 */
public final class ShipperCargoSnapshotContract {

    private ShipperCargoSnapshotContract() {
    }

    /** 荷主境界の Snapshot を引く経路。{@code {trackingNumber}} は追跡番号に置き換える。 */
    public static final String PATH = "/api/v1/bookings/shipper-snapshots/{trackingNumber}";

    /**
     * 荷主の貨物 Snapshot をまとめて引く経路。{@code shipperId} をクエリで渡す。
     *
     * <p>一覧を先に荷主で絞るための入口である。追跡側の直近 N 件から絞ると、
     * 貨物が増えた荷主の古い貨物が窓の外に落ちる。
     */
    public static final String BY_SHIPPER_PATH = "/api/v1/bookings/shipper-snapshots";

    /**
     * 追跡番号をまとめて渡すときのクエリ名（IT15）。
     *
     * <p>由来（{@code simulated}）を一覧の件数だけ問うために使う。1 件ずつ確かめると、
     * 例外が増えた日に問い合わせがその数だけ増える。
     */
    public static final String BY_TRACKING_NUMBERS_PARAM = "trackingNumbers";

    /** 呼び出してよい主体。 */
    public static final String CALLER_PRINCIPAL = "system:trackingms";

    /** 流れる項目。順序も含めて契約である。 */
    public static final List<String> FIELDS =
            List.of("bookingId", "trackingNumber", "shipperId", "simulated");
}
