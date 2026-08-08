package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingSearchCriteria;
import com.example.cargotracker.booking.application.internal.queryservices.BookingView;
import com.example.cargotracker.booking.domain.model.BookingCommandType;
import com.example.cargotracker.booking.domain.model.BookingStatus;
import com.example.cargotracker.booking.domain.model.CargoProgress;
import com.example.cargotracker.booking.domain.model.CargoRoutingStatus;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.shared.application.paging.Page;
import com.example.cargotracker.shared.application.paging.PageRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link BookingQueryService} の MyBatis 実装（読み取り専用アダプタ）。 */
@Service
public class MyBatisBookingQueryService implements BookingQueryService {

    private final BookingQueryMapper mapper;
    private final java.time.Clock clock;

    public MyBatisBookingQueryService(BookingQueryMapper mapper, java.time.Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Page<BookingView> search(BookingSearchCriteria criteria, PageRequest page) {
        // 空白だけの入力は「指定なし」として扱う（画面の入力欄をそのまま渡してくるため）
        BookingSearchCriteria trimmed = new BookingSearchCriteria(
                trim(criteria.origin()), trim(criteria.destination()),
                trim(criteria.status()), trim(criteria.trackingNumber()),
                criteria.shipperId());
        // **総件数は SQL で数える。** 全件を読んでから size() を取ると
        // ページ送りを入れた意味が無くなる。**荷主の絞り込みも同じ条件で数える** —
        // 数える条件と読む条件がずれると、他社の件数だけがページ送りに現れる
        long total = mapper.count(trimmed);
        return Page.of(
                mapper.search(trimmed, page.offset(), page.limit()).stream()
                        .map(this::toView)
                        .toList(),
                page, total);
    }

    @Override
    public Page<BookingView> findAwaitingRouting(PageRequest page) {
        long total = mapper.countAwaitingRouting();
        return Page.of(
                mapper.findAwaitingRouting(page.offset(), page.limit()).stream()
                        .map(this::toView)
                        .toList(),
                page, total);
    }

    @Override
    public Page<BookingView> findAwaitingTracking(PageRequest page) {
        long total = mapper.countAwaitingTracking();
        return Page.of(
                mapper.findAwaitingTracking(page.offset(), page.limit()).stream()
                        .map(this::toView)
                        .toList(),
                page, total);
    }

    @Override
    public Page<BookingView> findInTransit(PageRequest page) {
        long total = mapper.countInTransit();
        return Page.of(
                mapper.findInTransit(page.offset(), page.limit()).stream()
                        .map(this::toView)
                        .toList(),
                page, total);
    }

    @Override
    public Page<BookingView> findAwaitingNotification(PageRequest page) {
        long total = mapper.countAwaitingNotification();
        return Page.of(
                mapper.findAwaitingNotification(page.offset(), page.limit()).stream()
                        .map(this::toView)
                        .toList(),
                page, total);
    }

    @Override
    public Optional<BookingView> findById(String bookingId) {
        try {
            UUID id = UUID.fromString(bookingId);
            // 詳細では確定した旅程も読む。**一覧では読まない**（1 件のためだけに
            // 区間を引くと、予約の数だけクエリが飛ぶ）
            return Optional.ofNullable(mapper.findByBookingId(id))
                    .map(row -> toView(row, mapper.findItinerary(id)));
        } catch (IllegalArgumentException e) {
            // UUID として解釈できない ID は「見つからない」として扱う（500 にしない）
            return Optional.empty();
        }
    }

    /**
     * 残り日数に応じた文字色（{@code ui_design.md}「経路割り当て待ち一覧」）。
     *
     * <p><strong>3 日以内は赤、7 日以内は橙。</strong> 経路設計者が朝に見るのは
     * 「どれが一番切羽詰まっているか」であり、日付の数字だけでは一目で判断できない。
     * 期限を過ぎたものも赤で示す（見落としが最も痛い）。
     */
    private static String urgencyClass(long daysUntilDeadline) {
        if (daysUntilDeadline <= 3) {
            return "text-danger fw-bold";
        }
        if (daysUntilDeadline <= 7) {
            return "text-warning-emphasis fw-bold";
        }
        return "";
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private BookingView toView(BookingQueryRow row) {
        return toView(row, List.of());
    }

    private BookingView toView(BookingQueryRow row, List<ItineraryLegRow> legs) {
        BookingStatus status = BookingStatus.valueOf(row.getBookingStatus());
        // 残り日数は業務日付で数える。**UTC で数えると時差の分だけずれる**
        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.now(clock), row.getArrivalDeadline());
        CargoRoutingStatus routingStatus =
                CargoRoutingStatus.valueOf(row.getRoutingStatus());
        return new BookingView(
                row.getBookingId(),
                row.getShipperId(),
                row.getShipperCode(),
                row.getShipperName(),
                row.getShipperEmail(),
                row.getCargoType(),
                CargoType.valueOf(row.getCargoType()).displayName(),
                row.getWeight(),
                row.getOrigin(),
                row.getDestination(),
                row.getArrivalDeadline(),
                row.getBookingStatus(),
                status.displayName(),
                status.badgeClass(),
                daysLeft,
                urgencyClass(daysLeft),
                formatDimensions(row),
                row.getQuantity(),
                row.getDescription() == null ? "" : row.getDescription(),
                // **ボタンの出し分けは遷移表の述語をそのまま使う。**
                // ここで「PRELIMINARY なら」と書くと規則が 2 か所に散る
                status.canTransitionBy(BookingCommandType.ASSIGN_TO_ROUTING),
                status.canTransitionBy(BookingCommandType.CANCEL_BOOKING),
                // **確定の可否は経路の割り当ても見る**（遷移表 #4 の事前条件）。
                // 集約と同じ判断を使う（CargoProgress.confirmable が唯一の置き場）
                CargoProgress.confirmable(status, routingStatus),
                status.canTransitionBy(BookingCommandType.ASSIGN_TRACKING_NUMBER),
                row.getTrackingNumber() == null ? "" : row.getTrackingNumber(),
                // 荷受人は予約の時点では未確定でありうる（US16）
                row.getConsigneeName() == null ? "" : row.getConsigneeName(),
                row.getConsigneeAddress() == null ? "" : row.getConsigneeAddress(),
                row.getConsigneeEmail() == null ? "" : row.getConsigneeEmail(),
                routingStatus.displayName(),
                routingStatus.badgeClass(),
                legs.stream()
                        .map(leg -> new BookingView.ItineraryLegView(
                                leg.getVoyageNumber(),
                                leg.getLoadLocation(),
                                leg.getUnloadLocation(),
                                leg.getLoadTime(),
                                leg.getUnloadTime()))
                        .toList());
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
