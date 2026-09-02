package com.example.shared.contract;

import java.util.List;

/**
 * 利用者と荷主 ID の紐付けを引く REST の契約（US33）。
 *
 * <p>authms は利用者を持ち、bookingms は荷主を持つ。trackingms はこの契約を通じて、
 * ログイン利用者がどの荷主に紐付くかだけを知る。
 */
public final class UserShipperLinkContract {

    private UserShipperLinkContract() {
    }

    /** 紐付けを引く経路。{@code {username}} は利用者 ID に置き換える。 */
    public static final String PATH = "/api/v1/internal/user-shipper-links/{username}";

    /** 荷主向け追跡境界を判定するために呼ぶ主体。 */
    public static final String TRACKING_CALLER_PRINCIPAL = "system:trackingms";

    /** 同じ紐付けを参照してよい bookingms の主体。 */
    public static final String BOOKING_CALLER_PRINCIPAL = "system:bookingms";

    /** 流れる項目。未紐付けなら {@code shipperId} は null でよい。 */
    public static final List<String> FIELDS = List.of("linked", "shipperId");
}
