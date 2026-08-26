package com.example.bookingms;

import com.example.bookingms.application.port.BillableCargo;
import java.util.Collections;
import java.util.List;

/** 料金算出の入力に載せる旅程（[ADR-027] 決定 1 の改訂）。 */
public final class BillableCargoFixtures {

    private BillableCargoFixtures() {
    }

    /** 遠洋の区間を {@code count} 本。**係数は billingms が決める**——ここは区分だけを運ぶ。 */
    public static List<BillableCargo.Leg> oceanLegs(int count) {
        return Collections.nCopies(count, new BillableCargo.Leg("OCEAN", "OCEAN"));
    }
}
