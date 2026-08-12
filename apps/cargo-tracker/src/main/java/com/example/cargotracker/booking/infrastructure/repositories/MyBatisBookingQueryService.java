package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.application.internal.queryservices.DeadlineUrgency;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingSearchCriteria;
import com.example.cargotracker.booking.application.internal.queryservices.BookingView;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCommandType;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoProgress;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoRoutingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
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
                trim(criteria.routingStatus()), criteria.shipperId());
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
        UUID id;
        try {
            id = UUID.fromString(bookingId);
        } catch (IllegalArgumentException e) {
            // UUID として解釈できない ID は「見つからない」として扱う（500 にしない）。
            // **catch は解析だけを囲む。** 読み出しまで囲むと、行の復元が投げた
            // 例外が「見つかりません」に化けて、ログにも何も残らない（IT15 の P2）
            return Optional.empty();
        }
        // 詳細では確定した旅程も読む。**一覧では読まない**（1 件のためだけに
        // 区間を引くと、予約の数だけクエリが飛ぶ）
        return Optional.ofNullable(mapper.findByBookingId(id))
                .map(row -> toView(row, mapper.findItinerary(id)));
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
                new BookingView.ShipperSummary(
                        row.getShipperId(),
                        row.getShipperCode(),
                        row.getShipperName(),
                        row.getShipperEmail()),
                new BookingView.CargoSpec(
                        row.getCargoType(),
                        CargoType.valueOf(row.getCargoType()).displayName(),
                        row.getWeight(),
                        formatDimensions(row),
                        row.getQuantity(),
                        row.getDescription() == null ? "" : row.getDescription(),
                        specialHandling(row)),
                new BookingView.Delivery(
                        row.getOrigin(),
                        row.getDestination(),
                        row.getArrivalDeadline(),
                        daysLeft,
                        // **しきい値は規則である**（ADR-022）。ここが決めるのは
                        // 「何回・どの SQL で引くか」だけである
                        DeadlineUrgency.classOf(daysLeft),
                        legs.stream()
                                .map(leg -> new BookingView.ItineraryLegView(
                                        leg.getVoyageNumber(),
                                        leg.getLoadLocation(),
                                        leg.getUnloadLocation(),
                                        leg.getLoadTime(),
                                        leg.getUnloadTime(),
                                        leg.getCurrentLoadTime(),
                                        leg.getCurrentUnloadTime()))
                                .toList(),
                        // 荷受人は予約の時点では未確定でありうる（US16）
                        new BookingView.Consignee(
                                row.getConsigneeName() == null ? "" : row.getConsigneeName(),
                                row.getConsigneeAddress() == null ? "" : row.getConsigneeAddress(),
                                row.getConsigneeEmail() == null ? "" : row.getConsigneeEmail())),
                new BookingView.Status(
                        row.getBookingStatus(),
                        status.displayName(),
                        status.badgeClass(),
                        routingStatus.displayName(),
                        routingStatus.badgeClass(),
                        row.getMisroutedFrom(),
                        row.getMisroutedAt()),
                new BookingView.Tracking(
                        row.getTrackingNumber() == null ? "" : row.getTrackingNumber(),
                        row.getClaimCode() == null ? "" : row.getClaimCode()),
                // **ボタンの出し分けは遷移表の述語をそのまま使う。**
                // ここで「PRELIMINARY なら」と書くと規則が 2 か所に散る
                new BookingView.Actions(
                        status.canTransitionBy(BookingCommandType.ASSIGN_TO_ROUTING),
                        status.canCancelImmediately(),
                        status.requiresCancelApproval(),
                        // **確定の可否は経路の割り当ても見る**（遷移表 #4 の事前条件）。
                        // 集約と同じ判断を使う（CargoProgress.confirmable が唯一の置き場）
                        CargoProgress.confirmable(status, routingStatus),
                        status.canTransitionBy(BookingCommandType.ASSIGN_TRACKING_NUMBER)));
    }

    /**
     * 特別な取り扱いの表示用データを組み立てる（US05）。
     *
     * <p>どちらの記載も無ければ {@code null} を返す。**空の枠を画面に出さない。**
     */
    private static BookingView.SpecialHandlingView specialHandling(BookingQueryRow row) {
        boolean hazardous = row.getHazardousClass() != null;
        boolean refrigerated = row.getMinTemperature() != null || row.getMaxTemperature() != null;
        if (!hazardous && !refrigerated) {
            return null;
        }
        return new BookingView.SpecialHandlingView(
                hazardous ? row.getHazardousClass() : "",
                hazardous && row.getUnNumber() != null ? row.getUnNumber() : "",
                hazardous && row.getProperShippingName() != null
                        ? row.getProperShippingName() : "",
                refrigerated ? formatTemperature(row) : "");
    }

    private static String formatTemperature(BookingQueryRow row) {
        String unit = "FAHRENHEIT".equals(row.getTemperatureUnit()) ? "℉" : "℃";
        return "%s %s 〜 %s %s".formatted(
                row.getMinTemperature().stripTrailingZeros().toPlainString(), unit,
                row.getMaxTemperature().stripTrailingZeros().toPlainString(), unit);
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

    /** 誤配の件数（C34）。**一覧の絞り込みと同じ条件で数える。** */
    @Override
    public int countMisrouted() {
        return (int) mapper.count(
                BookingSearchCriteria.of(null, null, null, null, "MISROUTED"));
    }
}
