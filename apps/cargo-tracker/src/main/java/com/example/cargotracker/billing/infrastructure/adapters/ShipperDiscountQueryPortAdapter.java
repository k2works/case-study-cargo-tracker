package com.example.cargotracker.billing.infrastructure.adapters;

import com.example.cargotracker.billing.application.internal.outboundservices.ShipperDiscountQueryPort;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ShipperDiscountQueryPort} の ACL アダプター実装。
 *
 * <p>Billing BC から Shipper BC への境界を越えて荷主の割引率を取得する。
 * Booking BC の {@link BookingRepository} から予約を取得して荷主 ID を得た後、
 * {@link ShipperRepository} から荷主情報を取得する。
 */
@Component
public class ShipperDiscountQueryPortAdapter implements ShipperDiscountQueryPort {

    private final BookingRepository bookingRepository;
    private final ShipperRepository shipperRepository;

    public ShipperDiscountQueryPortAdapter(BookingRepository bookingRepository,
                                           ShipperRepository shipperRepository) {
        this.bookingRepository = bookingRepository;
        this.shipperRepository = shipperRepository;
    }

    /**
     * 予約 ID から荷主の割引率を取得する。
     *
     * <ul>
     *   <li>予約が存在しない場合は {@link BigDecimal#ZERO} を返す</li>
     *   <li>荷主が存在しない場合は {@link BigDecimal#ZERO} を返す</li>
     *   <li>荷主カテゴリが {@link CustomerCategory#CORPORATE} かつ
     *       法人契約情報が存在する場合のみ割引率を返す</li>
     *   <li>それ以外（個人荷主、VOLUME 等）は {@link BigDecimal#ZERO} を返す</li>
     * </ul>
     *
     * @param bookingId 予約 ID（UUID 文字列）
     * @return 割引率（0〜30）。割引なしの場合は {@link BigDecimal#ZERO}
     */
    @Override
    public BigDecimal findDiscountRateByBookingId(String bookingId) {
        BookingId bId;
        try {
            bId = new BookingId(UUID.fromString(bookingId));
        } catch (IllegalArgumentException e) {
            return BigDecimal.ZERO;
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(bId);
        if (bookingOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }

        ShipperId shipperId = bookingOpt.get().getShipperId();
        Optional<Shipper> shipperOpt = shipperRepository.findById(shipperId);
        if (shipperOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Shipper shipper = shipperOpt.get();
        if (shipper.getCategory() == CustomerCategory.CORPORATE
                && shipper.getCorporateContractInfo() != null) {
            return shipper.getCorporateContractInfo().discountRate();
        }

        return BigDecimal.ZERO;
    }
}
