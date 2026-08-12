package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 貨物予約の画面表示用データ（CQRS のクエリ側）。
 *
 * <p>荷主名は {@code shipper} テーブルを JOIN して 1 回の SQL で取る。
 * **予約 1 件ごとに荷主を引き直すと、一覧を開くたびに N+1 のクエリが飛ぶ。**
 * これは BC 間の直接参照ではない。読み取り側の SQL であり、Booking の
 * ドメインモデルは Shipper のモデルを知らないままである。
 *
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 34 個の要素が一列に並んでおり、<strong>同じ型の引数を取り違えても
 * コンパイルが通った</strong>。{@code shipperName} と {@code shipperEmail}、
 * {@code origin} と {@code destination} のように、隣り合う同型の引数は
 * 入れ替えても何も起きない。
 *
 * <p><strong>委譲するアクセサは畳んだ</strong>（IT18 の C1）。テンプレートは
 * {@code booking.delivery().origin()} のように入れ子をそのまま辿る。
 * 分割の効能は入れ子側で完結しており、<strong>委譲の層はテンプレート互換の
 * ためだけに存在していた</strong> —— レコードを分けるたびに増えるため、
 * 恒久化させずにここで返した。
 *
 * <p><strong>述語は残す。</strong> {@code isRouted()} のような判断をテンプレートに
 * 書き下すと、同じ規則が画面の数だけ散る。
 *
 * @param bookingId 予約 ID（文字列）
 * @param shipper   荷主（自社の予約かの判定に使う。US34）
 * @param cargo     貨物の仕様
 * @param delivery  引き渡し（出発地・目的地・期限・旅程・荷受人）
 * @param status    予約と経路の状態
 * @param tracking  追跡番号と引取確認コード
 * @param actions   いま実行できる操作
 */
public record BookingView(
        String bookingId,
        ShipperSummary shipper,
        CargoSpec cargo,
        Delivery delivery,
        Status status,
        Tracking tracking,
        Actions actions) {

    /**
     * 荷主（読み取り側の写し）。
     *
     * @param id    荷主 ID。<strong>自社の予約かの判定に使う</strong>（US34）
     * @param code  荷主コード
     * @param name  荷主名
     * @param email 荷主の連絡先
     */
    public record ShipperSummary(String id, String code, String name, String email) { }

    /**
     * 貨物の仕様。
     *
     * @param type            貨物種別（列挙子名）
     * @param typeLabel       貨物種別の表示名
     * @param weight          重量（kg）
     * @param dimensions      寸法（表示用に連結済み。未入力なら空文字）
     * @param quantity        個数（未入力なら {@code null}）
     * @param description     品名（未入力なら空文字）
     * @param specialHandling 危険物申告・温度管理条件（US05）。無ければ {@code null}
     */
    public record CargoSpec(
            String type,
            String typeLabel,
            BigDecimal weight,
            String dimensions,
            Integer quantity,
            String description,
            SpecialHandlingView specialHandling) { }

    /**
     * 引き渡し（どこから・どこへ・いつまでに・どの経路で・誰に）。
     *
     * @param origin          出発地 UN/LOCODE
     * @param destination     目的地 UN/LOCODE
     * @param arrivalDeadline 希望到着期限
     * @param daysUntilDeadline 希望期限までの残り日数（過ぎていれば負）
     * @param deadlineUrgencyClass 残り日数に応じた文字色のクラス（ui_design.md が正典）
     * @param itinerary       確定した旅程
     * @param consignee       荷受人（未登録なら空の値。US16）
     */
    public record Delivery(
            String origin,
            String destination,
            LocalDate arrivalDeadline,
            long daysUntilDeadline,
            String deadlineUrgencyClass,
            List<ItineraryLegView> itinerary,
            Consignee consignee) {

        public Delivery {
            itinerary = itinerary == null ? List.of() : List.copyOf(itinerary);
        }
    }

    /**
     * 荷受人（US16）。
     *
     * <p>未登録でも予約は成立する。国際輸送では荷受人が後から決まる。
     *
     * @param name    荷受人氏名。未登録なら空文字
     * @param address 荷受人住所。未登録なら空文字
     * @param email   荷受人の連絡先。未登録なら空文字
     */
    public record Consignee(String name, String address, String email) {

        /** 荷受人が登録済みか。 */
        public boolean isRegistered() {
            return name != null && !name.isBlank();
        }
    }

    /**
     * 予約と経路の状態。
     *
     * @param booking       予約状態（列挙子名）
     * @param label         予約状態の表示名
     * @param badgeClass    予約状態のバッジ用 Bootstrap クラス（ui_design.md が正典）
     * @param routingLabel  経路状態の表示名
     * @param routingBadgeClass 経路状態のバッジ用クラス
     * @param misroutedFrom 誤配として記録された場所。誤配でなければ {@code null}
     * @param misroutedAt   誤配を記録した時刻。同上
     */
    public record Status(
            String booking,
            String label,
            String badgeClass,
            String routingLabel,
            String routingBadgeClass,
            String misroutedFrom,
            Instant misroutedAt) {

        /**
         * 誤配として記録されているか（US28）。
         *
         * <p><strong>画面が判断を持たないようにする。</strong> 「経路状態が MISROUTED なら」と
         * 画面に書くと、同じ規則が 2 か所に散る。
         */
        public boolean isMisrouted() {
            return misroutedFrom != null && !misroutedFrom.isBlank();
        }

        /** 引き渡し済み以降か（US16）。 */
        public boolean isDelivered() {
            return BookingStatus.valueOf(booking).isDeliveredOrLater();
        }
    }

    /**
     * 追跡のための番号（US14 / US35）。
     *
     * @param number    追跡番号。発行前は空文字
     * @param claimCode 引取確認コード（US35）。確定前・旧い行では空文字。
     *                  <strong>公開追跡には渡さない</strong>（追跡番号は取引先へ転送される）
     */
    public record Tracking(String number, String claimCode) {

        /** 追跡番号が発行済みか。 */
        public boolean hasNumber() {
            return number != null && !number.isBlank();
        }

        /** 引取確認コードが採番されているか（US35）。確定前・旧い行では持たない。 */
        public boolean hasClaimCode() {
            return claimCode != null && !claimCode.isBlank();
        }
    }

    /**
     * いま実行できる操作。
     *
     * <p><strong>ボタンの出し分けは遷移表の述語をそのまま使う。</strong>
     * 画面で状態名を比べると、規則が 2 か所に散る。
     *
     * @param assignable       経路設計者に引き渡せるか
     * @param cancellable      <strong>即座に</strong>キャンセルできるか（遷移表 #9。輸送開始前）
     * @param cancelRequestable キャンセルの承認を申請できるか（US30。遷移表 #10。輸送中）
     * @param confirmable      予約を確定できるか（US13。経路の割り当てを含めて判断する）
     * @param trackingNumberIssuable 追跡番号を発行できるか（US14）
     */
    public record Actions(
            boolean assignable,
            boolean cancellable,
            boolean cancelRequestable,
            boolean confirmable,
            boolean trackingNumberIssuable) { }

    /**
     * 特別な取り扱いの記載があるか（US05）。
     *
     * <p><strong>述語は残す。</strong> 委譲アクセサは畳んだが（IT18 の C1）、
     * 判断はビューが持つ —— テンプレートで {@code cargo().specialHandling() != null} と
     * 書くと、<strong>同じ規則が画面の数だけ散る</strong>。
     */
    public boolean hasSpecialHandling() {
        return cargo.specialHandling() != null;
    }

    /** 経路が割り当てられているか。**割り当て済なら旅程がある。** */
    public boolean isRouted() {
        return !delivery.itinerary().isEmpty();
    }

    /**
     * 特別な取り扱いの表示用データ（US05）。
     *
     * <p><strong>危険物と温度を 1 つの型にまとめる。</strong> 貨物種別ごとに
     * どちらか一方しか付かないため、画面では「特別な取り扱いがあるか」だけを
     * 判断すれば足りる。
     *
     * @param hazardClass         危険物クラス。危険物でなければ空文字
     * @param unNumber            UN 番号。危険物でなければ空文字
     * @param properShippingName  正式輸送品名。危険物でなければ空文字
     * @param temperatureRange    温度帯の表示文字列。冷凍でなければ空文字
     */
    public record SpecialHandlingView(
            String hazardClass,
            String unNumber,
            String properShippingName,
            String temperatureRange) {

        /** 危険物申告があるか。 */
        public boolean isHazardous() {
            return !hazardClass.isBlank();
        }

        /** 温度管理条件があるか。 */
        public boolean isRefrigerated() {
            return !temperatureRange.isBlank();
        }
    }

    /**
     * 確定した旅程の区間 1 本（US11）。
     *
     * @param voyageNumber   航海番号
     * @param loadLocation   積込港
     * @param unloadLocation 荷降港
     * @param loadTime          積込予定日時（<strong>割り当てた時点の写し</strong>）
     * @param unloadTime        荷降予定日時（同上）
     * @param currentLoadTime   いまの航海スケジュール上の出発。便が無ければ {@code null}
     * @param currentUnloadTime いまの航海スケジュール上の到着。同上
     */
    public record ItineraryLegView(
            String voyageNumber,
            String loadLocation,
            String unloadLocation,
            Instant loadTime,
            Instant unloadTime,
            Instant currentLoadTime,
            Instant currentUnloadTime) {

        /**
         * 割り当てたあとに航海の日程が変わったか（IT11 / C9）。
         *
         * <p>区間は<strong>割り当てた時点の写し</strong>であり、航海を更新しても
         * 書き換わらない（確定した経路を勝手に作り直さないため）。そのため
         * 予約詳細は<strong>古い日時を表示し続ける</strong>。
         *
         * <p><strong>便が消えている場合は「変わった」と言わない。</strong>
         * 比べる相手が無いことを「違う」と呼ぶと、印の意味が薄まる。
         */
        public boolean scheduleChanged() {
            return (currentLoadTime != null && !currentLoadTime.equals(loadTime))
                    || (currentUnloadTime != null && !currentUnloadTime.equals(unloadTime));
        }
    }
}
