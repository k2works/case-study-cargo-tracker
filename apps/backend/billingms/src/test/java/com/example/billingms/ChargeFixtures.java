package com.example.billingms;

import com.example.billingms.domain.model.valueobjects.ChargeableLeg;
import com.example.billingms.domain.model.valueobjects.PortRegion;
import java.util.Collections;
import java.util.List;

/**
 * 料金の検査で使う旅程。
 *
 * <p><strong>国内だけの旅程は、地域区分を入れる前と同じ金額になる</strong>
 * （国内の係数は 1.0）。既存の検査がそのまま意味を保つのはそのためである
 * ——地域区分の追加は値上げではなく、国際輸送の過少請求を直す変更である。
 */
public final class ChargeFixtures {

    private ChargeFixtures() {
    }

    /** 国内の区間を {@code count} 本持つ旅程。 */
    public static List<ChargeableLeg> domesticLegs(int count) {
        return Collections.nCopies(count,
                new ChargeableLeg(PortRegion.DOMESTIC, PortRegion.DOMESTIC));
    }

    /** ACL が運ぶ形の、国内の区間 {@code count} 本。 */
    public static List<com.example.billingms.application.internal.outboundservices.acl.BillableCargoSnapshot.Leg>
            domesticSnapshotLegs(int count) {
        return Collections.nCopies(count,
                new com.example.billingms.application.internal.outboundservices.acl.BillableCargoSnapshot.Leg(
                        "DOMESTIC", "DOMESTIC"));
    }

    /** ACL が運ぶ形の、遠洋の区間 {@code count} 本。 */
    public static List<com.example.billingms.application.internal.outboundservices.acl.BillableCargoSnapshot.Leg>
            oceanSnapshotLegs(int count) {
        return Collections.nCopies(count,
                new com.example.billingms.application.internal.outboundservices.acl.BillableCargoSnapshot.Leg(
                        "OCEAN", "OCEAN"));
    }

    /** 遠洋の区間を {@code count} 本持つ旅程。 */
    public static List<ChargeableLeg> oceanLegs(int count) {
        return Collections.nCopies(count, new ChargeableLeg(PortRegion.OCEAN, PortRegion.OCEAN));
    }
}
