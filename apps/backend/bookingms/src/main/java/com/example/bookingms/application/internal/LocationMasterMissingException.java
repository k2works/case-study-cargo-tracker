package com.example.bookingms.application.internal;

/**
 * 地点マスタに載っているはずの地点が無い。
 *
 * <p><strong>こちら側の不備である。</strong>予約が持っている地点は登録時に検査を通っており、
 * それがマスタから消えているのは種データか複製の同期の問題（[ADR-014]）である。
 *
 * <p>だから<strong>利用者に作業を促さない</strong>。409 と「経路をもう一度探してください」で
 * 返すと、経路設計者は直らない作業を繰り返し、原因はどこにも残らない。
 * ハンドラを用意せず 500 に落として、記録に残す。
 *
 * <p>{@link IllegalStateException} を<strong>継承しない</strong>。継承すると、集約の状態違反を
 * 409 にしている広いハンドラに再び捕まる。
 */
public class LocationMasterMissingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LocationMasterMissingException(String unLocode) {
        super("地点マスタが見つかりません: " + unLocode);
    }
}
