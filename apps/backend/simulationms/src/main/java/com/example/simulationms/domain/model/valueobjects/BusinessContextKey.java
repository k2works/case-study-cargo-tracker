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

    /**
     * 誤配のあと、現在地から目的地へ向かう航海。
     *
     * <p><strong>元の航海では組み直せない。</strong>誤配した港からの区間を持たないため、
     * 経路設計者は現在地からの航海を探し、無ければ登録する——実業務と同じ手順である。
     */
    public static final String RECOVERY_VOYAGE_NUMBER = "recoveryVoyageNumber";

    /** 追跡番号発行が生んだ追跡番号。 */
    public static final String TRACKING_NUMBER = "trackingNumber";

    /** 通関申告が生んだ申告。 */
    public static final String DECLARATION_ID = "declarationId";

    /** 料金算出が生んだ精算書。 */
    public static final String INVOICE_NUMBER = "invoiceNumber";

    /** 何も生まない工程。 */
    public static final String NONE = "";

    /**
     * 「何番号か」を人に伝えるための和名。
     *
     * <p>画面が対訳表を持つと、名前を足したときに<strong>画面だけが古いまま</strong>になる。
     * 種別はサーバが返す。
     *
     * <p><strong>知らない名前は素通りさせない。</strong>名簿方式の検査は載せ忘れたものほど
     * 漏れる。足した名前をここに書き忘れたら、その場で落ちる方が安い。
     *
     * @return 和名。何も生まない工程（{@link #NONE}）なら {@code null}
     */
    public static String labelOf(String key) {
        return switch (key) {
            case NONE -> null;
            case RUN_ID -> "実行 ID";
            case SHIPPER_ID -> "荷主 ID";
            case BOOKING_ID -> "予約番号";
            case VOYAGE_NUMBER -> "航海番号";
            case RECOVERY_VOYAGE_NUMBER -> "組み直し用の航海番号";
            case TRACKING_NUMBER -> "追跡番号";
            case DECLARATION_ID -> "通関申告 ID";
            case INVOICE_NUMBER -> "精算書番号";
            default -> throw new IllegalArgumentException(
                    "和名の無い識別子です: " + key);
        };
    }

    private BusinessContextKey() {
    }
}
