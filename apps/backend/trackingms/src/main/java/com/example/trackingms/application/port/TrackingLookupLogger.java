package com.example.trackingms.application.port;

/**
 * 公開照会の記録（UC15 の最低保証・[ADR-024] 決定 7）。
 *
 * <p>認証が無い経路なので「誰が」は IP と {@code User-Agent} である。
 *
 * <p><strong>記録に失敗しても照会は返す。</strong>記録のために荷主の照会を止めない
 * ——ただし<strong>失敗を黙って捨てない</strong>。書けなかったことは警告として残す
 * （「例外にしない」は「記録しない」ではない）。
 */
public interface TrackingLookupLogger {

    /**
     * 照会を記録する。
     *
     * @param trackingNumber 照会された番号。<strong>読めない値もそのまま残す</strong>
     *     ——総当たりの手がかりになる
     * @param clientIp 呼び出し元
     * @param userAgent 名乗り。無いこともある
     * @param found 見つかったか。<strong>見つからなかった照会こそ材料である</strong>
     */
    void log(String trackingNumber, String clientIp, String userAgent, boolean found);
}
