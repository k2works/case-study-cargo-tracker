package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingView;
import com.example.cargotracker.booking.domain.model.BookingCommandType;
import com.example.cargotracker.booking.domain.model.BookingStatus;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.shared.application.paging.Page;
import com.example.cargotracker.shared.application.paging.PageRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link BookingQueryService} の MyBatis 実装（読み取り専用アダプタ）。 */
@Service
public class MyBatisBookingQueryService implements BookingQueryService {

    private final BookingQueryMapper mapper;

    public MyBatisBookingQueryService(BookingQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Page<BookingView> search(
            String origin, String destination, String status, PageRequest page) {
        String o = trim(origin);
        String d = trim(destination);
        String s = trim(status);
        // **総件数は SQL で数える。** 全件を読んでから size() を取ると
        // ページ送りを入れた意味が無くなる
        long total = mapper.count(o, d, s);
        return Page.of(
                mapper.search(o, d, s, page.offset(), page.size()).stream()
                        .map(MyBatisBookingQueryService::toView)
                        .toList(),
                page, total);
    }

    @Override
    public Optional<BookingView> findById(String bookingId) {
        try {
            return Optional.ofNullable(mapper.findByBookingId(UUID.fromString(bookingId)))
                    .map(MyBatisBookingQueryService::toView);
        } catch (IllegalArgumentException e) {
            // UUID として解釈できない ID は「見つからない」として扱う（500 にしない）
            return Optional.empty();
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BookingView toView(BookingQueryRow row) {
        BookingStatus status = BookingStatus.valueOf(row.getBookingStatus());
        return new BookingView(
                row.getBookingId(),
                row.getShipperCode(),
                row.getShipperName(),
                row.getCargoType(),
                CargoType.valueOf(row.getCargoType()).displayName(),
                row.getWeight(),
                row.getOrigin(),
                row.getDestination(),
                row.getArrivalDeadline(),
                row.getBookingStatus(),
                status.displayName(),
                status.badgeClass(),
                formatDimensions(row),
                row.getQuantity(),
                row.getDescription() == null ? "" : row.getDescription(),
                // **ボタンの出し分けは遷移表の述語をそのまま使う。**
                // ここで「PRELIMINARY なら」と書くと規則が 2 か所に散る
                status.canTransitionBy(BookingCommandType.CANCEL_BOOKING));
    }

    private static String formatDimensions(BookingQueryRow row) {
        if (row.getDimensionLength() == null) {
            return "";
        }
        return "%s × %s × %s cm".formatted(
                row.getDimensionLength().stripTrailingZeros().toPlainString(),
                row.getDimensionWidth().stripTrailingZeros().toPlainString(),
                row.getDimensionHeight().stripTrailingZeros().toPlainString());
    }
}
