package com.example.cargotracker.booking.application.internal.outboundservices;

import java.util.Optional;
import java.util.UUID;

/**
 * 追跡番号参照ポート。
 * booking コンテキストが tracking コンテキストの追跡番号を参照するための ACL インターフェース。
 *
 * <p>booking コンテキストは tracking コンテキストのドメインオブジェクト（TrackingEntry 等）を
 * 直接依存しない。このポートが両コンテキスト間の境界を明示する。
 */
public interface TrackingLookupPort {

    /**
     * 予約 ID に紐づく追跡番号を返す。
     *
     * <p>予約が未確定（PROVISIONAL）の場合や追跡番号がまだ発行されていない場合は
     * {@link Optional#empty()} を返す。
     *
     * @param bookingId 予約 ID
     * @return 追跡番号文字列（例: {@code TRK-ABCD1234}）、未発行の場合は empty
     */
    Optional<String> findTrackingNumberByBookingId(UUID bookingId);
}
