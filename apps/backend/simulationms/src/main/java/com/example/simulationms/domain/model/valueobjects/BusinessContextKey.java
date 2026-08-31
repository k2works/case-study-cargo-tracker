package com.example.simulationms.domain.model.valueobjects;

/**
 * 工程から工程へ引き継ぐ識別子の名前。
 *
 * <p>文字列を書き写すと、書き写し間違えた側だけが引き継げない。引き継ぎは工程をまたぐため、
 * 書き間違いは<strong>後ろの工程で初めて</strong>表面化する。
 */
public final class BusinessContextKey {

    /** 実行そのものの識別子。生成する業務データの名前をここから作る。 */
    public static final String RUN_ID = "runId";

    /** 荷主登録が生んだ荷主。 */
    public static final String SHIPPER_ID = "shipperId";

    /** 予約登録が生んだ予約。 */
    public static final String BOOKING_ID = "bookingId";

    /** 航海登録が生んだ航海。 */
    public static final String VOYAGE_NUMBER = "voyageNumber";

    /** 追跡番号発行が生んだ追跡番号。 */
    public static final String TRACKING_NUMBER = "trackingNumber";

    /** 通関申告が生んだ申告。 */
    public static final String DECLARATION_ID = "declarationId";

    /** 料金算出が生んだ精算書。 */
    public static final String INVOICE_NUMBER = "invoiceNumber";

    /** 何も生まない工程。 */
    public static final String NONE = "";

    private BusinessContextKey() {
    }
}
