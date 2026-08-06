package com.example.cargotracker.booking.domain.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * 予約状態。
 *
 * <p><strong>遷移の正典は {@code docs/design/domain-model.md}「BookingStatus 状態遷移表」である。</strong>
 * 本列挙型はその表を実行可能な形にしたものであり、表に無い遷移はすべて拒否する。
 * 画面のボタン出し分け（{@code ui_design.md}）は独自の判定を持たず、
 * {@link #canTransitionBy} を呼ぶ。**同じ規則を 2 か所に書くと、必ず片方だけが更新される。**
 */
public enum BookingStatus {

    /** 仮受付。予約登録直後。 */
    PRELIMINARY("仮受付"),

    /** 経路提案済。経路設計者に引き渡した状態。 */
    ROUTE_PROPOSED("経路提案済"),

    /** 確認済。経路が割り当てられ予約が確定した状態。 */
    CONFIRMED("確認済"),

    /** 追跡番号発行済。 */
    TRACKING_ISSUED("追跡番号発行済"),

    /** 輸送中。 */
    IN_TRANSIT("輸送中"),

    /** 配達完了。 */
    DELIVERED("配達完了"),

    /** 精算済。終端状態。 */
    SETTLED("精算済"),

    /** キャンセル。終端状態。 */
    CANCELLED("キャンセル");

    private static final Map<BookingStatus, Map<BookingCommandType, BookingStatus>> TRANSITIONS =
            buildTransitionTable();

    private final String displayName;

    BookingStatus(String displayName) {
        this.displayName = displayName;
    }

    /** 画面・メッセージに出す日本語名。**列挙子名を利用者に見せない**（`creating-manual` の表記規約）。 */
    public String displayName() {
        return displayName;
    }

    /** 新規予約の初期状態（遷移表 #1）。 */
    public static BookingStatus initial() {
        return PRELIMINARY;
    }

    /** 終端状態はいかなるコマンドも受け付けない。 */
    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /**
     * コマンドを実行できるか。
     *
     * <p>画面のボタン出し分けはこの述語をそのまま呼ぶ。**「押せるのに実行すると失敗する」
     * ボタンは、利用者から見て壊れているのと同じである。**
     */
    public boolean canTransitionBy(BookingCommandType command) {
        return TRANSITIONS.get(this).containsKey(command);
    }

    /**
     * コマンドによる遷移後の状態を返す。
     *
     * @throws InvalidBookingStatusTransitionException 遷移表に無い遷移のとき
     */
    public BookingStatus transitionBy(BookingCommandType command) {
        BookingStatus next = TRANSITIONS.get(this).get(command);
        if (next == null) {
            throw new InvalidBookingStatusTransitionException(this, command);
        }
        return next;
    }

    private static Map<BookingStatus, Map<BookingCommandType, BookingStatus>> buildTransitionTable() {
        Map<BookingStatus, Map<BookingCommandType, BookingStatus>> table =
                new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : values()) {
            table.put(status, new EnumMap<>(BookingCommandType.class));
        }

        // 遷移表（domain-model.md）の #2〜#10。#1 は遷移元を持たない新規作成のため含めない。
        table.get(PRELIMINARY).put(BookingCommandType.ASSIGN_TO_ROUTING, ROUTE_PROPOSED);
        // #3 は状態を変えない。RoutingStatus のみが ROUTED になる
        table.get(ROUTE_PROPOSED).put(BookingCommandType.ROUTE_CARGO, ROUTE_PROPOSED);
        // #4 の事前条件「RoutingStatus = ROUTED」は状態だけでは判定できないため Cargo が守る
        table.get(ROUTE_PROPOSED).put(BookingCommandType.CONFIRM_BOOKING, CONFIRMED);
        table.get(CONFIRMED).put(BookingCommandType.ASSIGN_TRACKING_NUMBER, TRACKING_ISSUED);
        table.get(TRACKING_ISSUED).put(BookingCommandType.START_TRANSPORT, IN_TRANSIT);
        table.get(IN_TRANSIT).put(BookingCommandType.COMPLETE_DELIVERY, DELIVERED);
        table.get(DELIVERED).put(BookingCommandType.SETTLE_BOOKING, SETTLED);

        // #9 輸送開始前のキャンセル。#10 輸送中のキャンセル（承認を伴う。US30）。
        // DELIVERED 以降はキャンセルできない（引き渡し済み貨物の取り消しは返送であり別業務）
        for (BookingStatus cancellable :
                new BookingStatus[] {PRELIMINARY, ROUTE_PROPOSED, CONFIRMED, TRACKING_ISSUED, IN_TRANSIT}) {
            table.get(cancellable).put(BookingCommandType.CANCEL_BOOKING, CANCELLED);
        }

        return table;
    }
}
