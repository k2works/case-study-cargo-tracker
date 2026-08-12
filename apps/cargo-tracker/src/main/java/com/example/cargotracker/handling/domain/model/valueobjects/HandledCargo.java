package com.example.cargotracker.handling.domain.model.valueobjects;

/**
 * 作業の対象となった貨物。<strong>読み取った番号と、引き当てた予約のひと組</strong>。
 *
 * <p>この 2 つを別々に持つと、<strong>成り立たない組み合わせを作れる</strong>。
 *
 * <ul>
 *   <li>予約 ID はあるが、読み取った番号が無い（どこから引き当てたか分からない）</li>
 *   <li>読み取った番号はあるが、予約 ID が無い（どの貨物の作業か分からない）</li>
 *   <li>読み取った番号と、まったく別の予約 ID の組み合わせ</li>
 * </ul>
 *
 * <p>いずれも荷役作業としては意味をなさない。<strong>引き当てはひとつながりの行為であり、
 * 結果もひと組で扱う。</strong>
 *
 * <p>Checkstyle のパラメータ数上限に当たって作った箱ではない。
 * <strong>上限に当たったことを、概念を見直す合図として使った</strong>
 * （IT6 の {@code ProposedRoute.Path}・{@code CargoProgress} と同じ判断）。
 *
 * @param scannedTrackingNumber 読み取った追跡番号
 * @param bookingId             引き当てた予約 ID
 */
public record HandledCargo(
        ScannedTrackingNumber scannedTrackingNumber,
        CargoBookingId bookingId) {

    public HandledCargo {
        if (scannedTrackingNumber == null) {
            throw new IllegalArgumentException("読み取った追跡番号は必須です");
        }
        if (bookingId == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
    }
}
