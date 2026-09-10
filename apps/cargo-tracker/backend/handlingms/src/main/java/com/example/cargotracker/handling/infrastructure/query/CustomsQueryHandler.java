package com.example.cargotracker.handling.infrastructure.query;

import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import com.example.cargotracker.handling.domain.model.valueobjects.HolidayCalendar;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsDeclarationMapper;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsDeclarationMapper.CustomsDeclarationRow;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsStatusHistoryMapper;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CountOverdueCustomsHoldsQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsDeclarationListView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsDeclarationView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsHistoryEntryView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsHistoryView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsDeclarationQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsDeclarationsQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsHistoryQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsStatusOfCargoQuery;
import com.example.cargotracker.shared.domain.location.CountryCode;
import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/**
 * 通関申告の読み取り（US29 §受入基準 3・6・7）。
 *
 * <p><b>留置営業日数は読むときに数える。</b> 留置中は日が経つだけで日数が変わるのに
 * イベントは来ないので、列に持つと古いままになる。休日カレンダーを知っているのは
 * ドメインなので、SQL に写さない——同じ判定が 2 か所にあると、片方だけ直る。</p>
 *
 * <p><b>並べ替えもここで行う。</b> 数える場所と並べる場所が離れると、
 * 並び順だけが古い値で決まる。</p>
 */
@Component
public class CustomsQueryHandler {

    /** 督促の対象になる留置の営業日数（US29 §受入基準 6）。 */
    public static final int OVERDUE_BUSINESS_DAYS = 3;

    /** 一覧の取り込み上限。読むときに数えて並べ替えるので、先に多めに取る。 */
    private static final int FETCH_LIMIT = 500;

    private final CustomsDeclarationMapper declarations;
    private final CustomsStatusHistoryMapper history;
    private final Clock clock;

    public CustomsQueryHandler(CustomsDeclarationMapper declarations,
            CustomsStatusHistoryMapper history, Clock clock) {
        this.declarations = declarations;
        this.history = history;
        this.clock = clock;
    }

    /**
     * 状態の変更履歴（US29 §受入基準 8）。
     *
     * <p><b>正典は当初「Event Store から読む」だった。この版では読めない</b>ので
     * 投影にした（{@code CustomsDeclarationProjection}）。主キーは元イベントの
     * 識別子なので、リプレイで積み上がらない。</p>
     */
    @QueryHandler
    public CustomsHistoryView handle(FindCustomsHistoryQuery query) {
        return new CustomsHistoryView(history.findHistory(query.declarationNumber()).stream()
                .map(row -> new CustomsHistoryEntryView(row.kind(), row.previousStatus(),
                        row.status(),
                        row.status() == null ? null : CustomsStatus.valueOf(row.status()).label(),
                        row.reason(), row.changedBy(), row.changedAt()))
                .toList());
    }

    @QueryHandler
    public CustomsDeclarationListView handle(FindCustomsDeclarationsQuery query) {
        // **上限より 1 件多く引いて、切れたかどうかを判別する**（IT10 レビュー N6 と
        // 同じ形）。件数が上限ちょうどのときに「切れた」と言うと、警告が常時
        // 点灯して合図として働かなくなる。
        List<CustomsDeclarationRow> rows = declarations.search(
                query.includeCleared(), query.trackingNumber(), query.status(),
                FETCH_LIMIT + 1);
        boolean truncated = rows.size() > FETCH_LIMIT;
        List<CustomsDeclarationView> items = rows.stream()
                .limit(FETCH_LIMIT)
                .map(this::toView)
                .filter(view -> !query.overdueOnly() || view.overdue())
                // **督促の対象が先に来る。** 留置営業日の多い順（ui_design.md）。
                .sorted(Comparator.comparingInt(CustomsDeclarationView::heldBusinessDays)
                        .reversed()
                        .thenComparing(CustomsDeclarationView::declaredAt)
                        .thenComparing(CustomsDeclarationView::declarationNumber))
                .toList();
        return new CustomsDeclarationListView(items, items.size(), truncated);
    }

    @QueryHandler
    public CustomsDeclarationView handle(FindCustomsDeclarationQuery query) {
        CustomsDeclarationRow row = declarations.findByNumber(query.declarationNumber());
        return row == null ? null : toView(row);
    }

    /**
     * その貨物の最新の通関状態（引取のガードが読む）。
     *
     * <p>申告が無ければ {@code null} を返す。<b>「無い」と「審査中」は違う</b>——
     * 前者はまだ申告していない、後者は申告して審査を待っている。</p>
     */
    @QueryHandler
    public CustomsDeclarationView handle(FindCustomsStatusOfCargoQuery query) {
        CustomsDeclarationRow row = declarations.findLatestByCargo(query.trackingNumber());
        return row == null ? null : toView(row);
    }

    /** 督促の対象の件数（S02。**件数は次の行動へ繋ぐ**ので一覧と同じ判定で数える）。 */
    @QueryHandler
    public Integer handle(CountOverdueCustomsHoldsQuery query) {
        return (int) declarations.findHeld().stream()
                .map(this::toView)
                .filter(CustomsDeclarationView::overdue)
                .count();
    }

    private CustomsDeclarationView toView(CustomsDeclarationRow row) {
        CustomsStatus status = CustomsStatus.valueOf(row.status());
        int heldBusinessDays = heldBusinessDaysOf(row, status);
        return new CustomsDeclarationView(row.declarationNumber(), row.trackingNumber(),
                row.bookingId(), row.status(), status.label(), row.declaredAt(),
                row.lastStatusChangedAt(), row.lastHeldAt(), heldBusinessDays,
                status == CustomsStatus.HELD && heldBusinessDays > OVERDUE_BUSINESS_DAYS,
                row.lastReason(), row.changedBy());
    }

    /**
     * 留置してからの営業日数。
     *
     * <p>留置中なら今日まで、<b>留置から出たあとは出た時刻まで</b>数える。留置した
     * ことが無ければ 0。</p>
     *
     * <p><b>列は読まない。</b> {@code held_business_days} は投影が 0 のまま置いて
     * いる——契約イベントが確定値を持つが、投影は内部イベントだけを読むので写す
     * 相手がいない。列を返すと、5 営業日留置されて通関済になった申告が「留置
     * 0 営業日」に見える。料金調整の根拠（US21）がそこで消える。</p>
     */
    private int heldBusinessDaysOf(CustomsDeclarationRow row, CustomsStatus status) {
        if (row.lastHeldAt() == null) {
            // 留置したことが無い。
            return 0;
        }
        // 留置中は今日まで、決着していれば決着した日まで。**同じ数え方を使う**
        // ——数える場所を分けると、決着の前後で日数が飛ぶ。
        Instant until = status == CustomsStatus.HELD
                ? clock.instant()
                : row.lastStatusChangedAt();
        LocalDate heldFrom = businessDate(row.lastHeldAt());
        LocalDate today = businessDate(until);
        if (heldFrom.isAfter(today)) {
            // 留置がまだ来ていない。時計のずれや入力の誤りで起こりうるが、
            // **一覧全体を落とさない**——1 件の変な行のために、他の申告まで
            // 読めなくなるほうが困る。数えるものが無いので 0 にする。
            return 0;
        }
        return HolidayCalendar.of(new CountryCode("JP")).businessDaysBetween(heldFrom, today);
    }

    private static LocalDate businessDate(Instant at) {
        return LocalDate.ofInstant(at, BusinessClockConfiguration.BUSINESS_ZONE);
    }
}
