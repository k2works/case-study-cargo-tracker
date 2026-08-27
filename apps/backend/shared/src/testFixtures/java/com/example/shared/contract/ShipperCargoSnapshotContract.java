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

    /** 呼び出してよい主体。 */
    public static final String CALLER_PRINCIPAL = "system:trackingms";

    /** 流れる項目。順序も含めて契約である。 */
    public static final List<String> FIELDS =
            List.of("bookingId", "trackingNumber", "shipperId");
}
