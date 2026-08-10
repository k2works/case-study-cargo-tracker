package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .BillableCargoPort;
import com.example.cargotracker.booking.application.internal.outboundservices.acl
        .CargoCorrectionRequests;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.CargoExceptions;
import com.example.cargotracker.booking.infrastructure.repositories.BillableCargoRow;
import com.example.cargotracker.booking.infrastructure.repositories.BookingQueryMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link BillableCargoPort} の実装（ACL のアダプタ。US21）。
 *
 * <p><strong>返すのは素の値だけである</strong>（ADR-005）。{@code Cargo} を返すと
 * Billing が Booking のドメインを参照することになる（ArchUnit ルール 4）。
 *
 * <p><strong>SQL では他 BC のテーブルを触らない</strong>（ADR-015）。
 * 訂正の申請中は {@code CargoCorrectionRequests}（→ Handling）、
 * 例外の有無は {@code CargoExceptions}（→ Tracking）で受け取る。
 * <strong>ここで JOIN すると、越境の許容リストがまた伸びる</strong>
 * （IT13 で 2 行返したばかりである）。
 *
 * <p><strong>距離係数は区間数である。</strong> 実距離を持っていないため、経由の多さを
 * 距離の代わりに使う。ADR-008 の概算式とは別物である
 * （あちらは経路候補の並べ替え用であり、請求には使わない）。
 */
@Component
public class BillableCargoAdapter implements BillableCargoPort {

    /** 引取まで済んだ予約の状態。 */
    private static final String DELIVERED = "DELIVERED";

    private final BookingQueryMapper mapper;
    private final CargoCorrectionRequests correctionRequests;
    private final CargoExceptions cargoExceptions;

    public BillableCargoAdapter(
            BookingQueryMapper mapper,
            CargoCorrectionRequests correctionRequests,
            CargoExceptions cargoExceptions) {
        this.mapper = mapper;
        this.correctionRequests = correctionRequests;
        this.cargoExceptions = cargoExceptions;
    }

    @Override
    public List<BillableCargoSummary> findPending() {
        return mapper.findBillable().stream()
                .map(this::toSummary)
                // **訂正・取り消しの申請中は請求対象に出さない**（IT12 持ち越し C8）。
                // 取り消されるかもしれない引取をもとに請求書を出すと、
                // 出した後で引取が無かったことになる
                .filter(cargo -> !cargo.correctionRequested())
                .toList();
    }

    @Override
    public Optional<BillableCargoSummary> findByBookingId(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(bookingId.strip());
        } catch (IllegalArgumentException e) {
            // **形式の違う ID を例外にしない。** 画面が 500 になる
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findBillableByBookingId(id)).map(this::toSummary);
    }

    private BillableCargoSummary toSummary(BillableCargoRow row) {
        String bookingId = row.getBookingId().toString();
        return new BillableCargoSummary(
                bookingId,
                row.getTrackingNumber() == null ? "" : row.getTrackingNumber(),
                row.getShipperId().toString(),
                row.getShipperName(),
                "CORPORATE".equals(row.getShipperType()),
                row.getOrigin(),
                row.getDestination(),
                row.getCargoType(),
                row.getWeight(),
                // **区間が 0 本の貨物は請求できない。** 運んでいない
                BigDecimal.valueOf(Math.max(row.getLegCount(), 0)),
                // 一覧のクエリは DELIVERED だけを返すため、状態が空なら引取済とみなす
                row.getBookingStatus() == null || DELIVERED.equals(row.getBookingStatus()),
                hasPendingCorrection(bookingId),
                !cargoExceptions.findByTrackingNumber(row.getTrackingNumber()).isEmpty());
    }

    private boolean hasPendingCorrection(String bookingId) {
        return correctionRequests.findByBookingId(bookingId).stream()
                // **述語は運んできた値をそのまま使う。** ここで状態名を書き直さない
                .anyMatch(CargoCorrectionRequests.CorrectionSummary::pending);
    }
}
