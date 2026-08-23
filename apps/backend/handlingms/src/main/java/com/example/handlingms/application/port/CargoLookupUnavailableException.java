package com.example.handlingms.application.port;

/**
 * 貨物を確かめられなかった（[ADR-023] 決定 2）。
 *
 * <p>「見つからなかった」とは違う。混ぜると、bookingms が落ちているときに荷役作業員へ
 * 「その追跡番号は存在しません」と伝わり、<strong>作業員は番号を疑って打ち直し続ける</strong>。
 * 直らない作業をさせたうえ、原因はどこにも残らない（[ADR-019] と同じ形）。
 */
public class CargoLookupUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CargoLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
