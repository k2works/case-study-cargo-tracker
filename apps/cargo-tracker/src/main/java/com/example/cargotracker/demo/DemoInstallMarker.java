package com.example.cargotracker.demo;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingSearchCriteria;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Shipper;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 動作確認用データを投入済みかを判定する。
 *
 * <p><strong>印にできるのは、投入する側しか作らないものだけである</strong>（IT19 で 2 回踏んだ）。
 *
 * <ul>
 *   <li>「見積が 1 件でもあれば投入済み」——<strong>見積を作る別のテストが先に走ると、
 *       投入が丸ごと飛んで章が空のまま緑になった</strong></li>
 *   <li>「動作確認用の荷主が居れば投入済み」——<strong>その荷主は {@code db/demo} の
 *       {@code V900} が SQL で作る</strong>。local を起動すると必ず先に居るため、
 *       投入が一度も走らなかった</li>
 * </ul>
 *
 * <p>そこで<strong>動作確認用の荷主に予約があるか</strong>を印にする。荷主は SQL が作るが、
 * <strong>その荷主の予約を作るのはここだけ</strong>である。
 */
@Component
class DemoInstallMarker {

    /** {@code db/demo} の {@code V900} が入れる荷主（山田商事）の連絡先。 */
    static final String DEMO_SHIPPER_EMAIL = "shipper-sample@example.com";

    private final ShipperRepository shippers;
    private final BookingQueryService bookings;

    DemoInstallMarker(ShipperRepository shippers, BookingQueryService bookings) {
        this.shippers = shippers;
        this.bookings = bookings;
    }

    boolean alreadyInstalled() {
        Optional<Shipper> shipper = shippers.findByEmail(new Email(DEMO_SHIPPER_EMAIL));
        return shipper.isPresent()
                && !bookings.search(
                        BookingSearchCriteria.of(null, null, null, null)
                                .scopedTo(shipper.get().id().value()),
                        PageRequest.of(1))
                .items().isEmpty();
    }
}
