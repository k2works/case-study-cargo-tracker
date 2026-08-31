package com.example.simulationms.application.internal.outboundservices.acl;

/**
 * 工程から工程へ引き継ぐ識別子の名前（[ADR-030] 決定 5）。
 *
 * <p>文字列を書き写すと、書き写し間違えた側だけが「引き継げていない」状態になる。
 * 引き継ぎは工程をまたぐため、書き間違いは<strong>後ろの工程で初めて</strong>表面化する。
 */
public final class BusinessContext {

    /** 実行そのものの識別子。生成する業務データの名前をここから作る。 */
    public static final String RUN_ID = "runId";

    /** 荷主登録が生んだ荷主。 */
    public static final String SHIPPER_ID = "shipperId";

    /** 予約登録が生んだ予約。 */
    public static final String BOOKING_ID = "bookingId";

    /** 追跡番号発行が生んだ追跡番号。 */
    public static final String TRACKING_NUMBER = "trackingNumber";

    private BusinessContext() {
    }
}
