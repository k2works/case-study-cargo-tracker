package com.example.cargotracker.booking.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 貨物予約の投影テーブル（data-model.md「booking_read_db」）。 */
@Mapper
public interface CargoSummaryMapper {

    /**
     * 予約番号は投影側で採番する。集約で MAX+1 しない（data-model.md）。
     *
     * <p>日付は「いつ受け付けたか」を読めるように入れ、連番は全体で一意にする。
     * 日ごとの連番にすると、日をまたぐ境目で衝突を避ける仕掛けが要る。</p>
     */
    @Select("SELECT 'B-' || to_char(#{bookedOn}::date, 'YYYY-MMDD') || '-' "
            + "|| lpad(nextval('booking_number_seq')::text, 4, '0')")
    String nextBookingNumber(@Param("bookedOn") LocalDate bookedOn);

    int insert(CargoSummaryRow row);

    CargoSummaryRow findById(@Param("bookingId") String bookingId);

    /**
     * 一覧（S20）。既定では精算済とキャンセルを外し、到着期限が近い順に並べる
     * （ui_design.md「一覧の既定条件」）。終わった予約が混ざると、一覧全体が
     * 「今日やること」として信用されなくなる。
     */
    List<CargoSummaryRow> findAll(@Param("includeFinished") boolean includeFinished,
            @Param("limit") int limit, @Param("offset") int offset);

    int countAll(@Param("includeFinished") boolean includeFinished);

    /**
     * 経路設計の依頼を投影に反映する（US06）。
     *
     * <p>常に INSERT する形にしない。状態の更新で行を増やすと、作成しかない
     * イテレーションでは成立し、最初の更新ストーリーで壊れる。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET booking_status = #{bookingStatus}, "
            + "routing_status = #{routingStatus}, "
            + "routing_requested_at = #{requestedAt}, projected_at = #{projectedAt} "
            + "WHERE booking_id = #{bookingId}")
    int updateRoutingRequested(@Param("bookingId") String bookingId,
            @Param("bookingStatus") String bookingStatus,
            @Param("routingStatus") String routingStatus,
            @Param("requestedAt") Instant requestedAt,
            @Param("projectedAt") Instant projectedAt);

    /**
     * 経路の確定を投影に反映する（US09）。
     *
     * <p><b>{@code booking_status} は触らない。</b> 経路が付いても、荷主に通知する
     * まで予約は提案中である（US12）。ここで確定にすると、荷主が知らないうちに
     * 予約が確定したことになる。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET routing_status = #{routingStatus}, "
            // 組み直したので、戻された理由は役目を終える。残すと経路設計者は
            // 次に開いたときも「営業から戻されました」を読み続ける。
            + "returned_to_routing_at = NULL, return_reason = NULL, "
            + "projected_at = #{projectedAt} WHERE booking_id = #{bookingId}")
    int updateRoutingStatus(@Param("bookingId") String bookingId,
            @Param("routingStatus") String routingStatus,
            @Param("projectedAt") Instant projectedAt);

    /**
     * 仮受付の予約情報の修正を投影に反映する（US32）。
     *
     * <p>付帯情報（危険物申告・温度条件）も含めて<b>丸ごと書き換える</b>。残すと、
     * 一般貨物に直したのに危険物申告が付いたままの行ができる。</p>
     *
     * <p>戻り値で「書けたか」を見る。0 なら投影にその予約が無い。</p>
     */
    int updateSpecification(CargoSummaryRow row);

    /**
     * 条件の調整を投影に反映する（US10 / ADR-0009 決定 3）。
     *
     * <p>期限を書き換え、経路設計をやり直しにする。<b>{@code cargo_leg} は消さない</b>
     * （再設計で入れ替わるまで残す）。消すと「何を組み直すのか」が分からなくなる。</p>
     *
     * <p><b>差し戻しの記録も同時に消す。</b> 条件が変わったのだから、営業の手番は
     * 終わっている。残すと S02 に出たままになり、営業は何度も同じ予約を開く。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET arrival_deadline = #{arrivalDeadline}, "
            + "route_exclude_unlocodes = #{excludeUnLocodes}, "
            + "route_depart_from_unlocode = #{departFromUnLocode}, "
            + "routing_status = #{routingStatus}, condition_review_requested_at = NULL, "
            + "condition_review_reason = NULL, "
            // **通知済みの印も落とす。** 条件が変われば、荷主へ伝えた経路は
            // その条件で組んだものではなくなる。残すと、組み直しても営業の
            // 「未通知」に二度と出ず、旧経路を伝えたまま誰も気づけない
            // （IT6 レビュー 中）。履歴（cargo_notification）は残る。
            + "last_notified_at = NULL, projected_at = #{projectedAt} "
            + "WHERE booking_id = #{bookingId}")
    int updateAdjustedRouteSpecification(@Param("bookingId") String bookingId,
            @Param("arrivalDeadline") LocalDate arrivalDeadline,
            @Param("excludeUnLocodes") String excludeUnLocodes,
            @Param("departFromUnLocode") String departFromUnLocode,
            @Param("routingStatus") String routingStatus,
            @Param("projectedAt") Instant projectedAt);

    /**
     * 経路探索の条件（US10）。**候補を出すたびにここから組む。**
     *
     * <p>画面から組み立てて送ると、条件を直したのに古い条件で探すことが起きる。</p>
     */
    @Select("SELECT route_exclude_unlocodes AS exclude_unlocodes, "
            + "route_depart_from_unlocode AS depart_from_unlocode "
            + "FROM cargo_summary WHERE booking_id = #{bookingId}")
    RouteConditionRow findRouteCondition(@Param("bookingId") String bookingId);

    /** 調整された探索条件。どちらも未調整なら null。 */
    record RouteConditionRow(String excludeUnlocodes, String departFromUnlocode) {
    }

    /**
     * 差し戻しを投影に反映する（US10 §4 / ADR-0009 決定 1）。
     *
     * <p><b>{@code routing_status} は触らない。</b> 状態を戻すと「一度も設計して
     * いない予約」と混ざり、S30 の一覧から消えて誰も再開しない。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET condition_review_requested_at = #{requestedAt}, "
            + "condition_review_reason = #{reason}, projected_at = #{projectedAt} "
            + "WHERE booking_id = #{bookingId}")
    int updateConditionReview(@Param("bookingId") String bookingId,
            @Param("requestedAt") Instant requestedAt,
            @Param("reason") String reason,
            @Param("projectedAt") Instant projectedAt);

    /**
     * 荷主への通知を投影に反映する（US12）。
     *
     * <p>状態は {@code ROUTE_NOTIFIED} になり、{@code last_notified_at} が動く。
     * 履歴は {@code cargo_notification} が持つ。<b>ここで数えない</b>——営業の
     * ダッシュボードは「まだ通知していない予約」を履歴テーブルを数えずに絞る。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET booking_status = #{bookingStatus}, "
            + "last_notified_at = #{notifiedAt}, projected_at = #{projectedAt} "
            + "WHERE booking_id = #{bookingId}")
    int updateNotified(@Param("bookingId") String bookingId,
            @Param("bookingStatus") String bookingStatus,
            @Param("notifiedAt") Instant notifiedAt,
            @Param("projectedAt") Instant projectedAt);

    /**
     * 経路設計への差し戻しを投影に反映する（US12）。
     *
     * <p><b>{@code routing_requested_at} は触らない。</b> 引き渡した日時と、通知後に
     * 戻した日時は別のことである。同じ列に書くと履歴で区別できなくなる。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET booking_status = #{bookingStatus}, "
            + "routing_status = #{routingStatus}, returned_to_routing_at = #{returnedAt}, "
            + "return_reason = #{reason}, "
            // 戻したのだから、伝えた経路はもう有効でない。組み直したあと
            // 営業の「未通知」に再び出るようにする。履歴は残る。
            + "last_notified_at = NULL, projected_at = #{projectedAt} "
            + "WHERE booking_id = #{bookingId}")
    int updateReturnedToRouting(@Param("bookingId") String bookingId,
            @Param("bookingStatus") String bookingStatus,
            @Param("routingStatus") String routingStatus,
            @Param("returnedAt") Instant returnedAt,
            @Param("reason") String reason,
            @Param("projectedAt") Instant projectedAt);

    /**
     * 荷主へ通知していない経路確定済みの予約の件数（S02 / 営業。US12）。
     *
     * <p>履歴テーブルを数えず {@code last_notified_at} で絞る。</p>
     */
    @Select("SELECT count(*) FROM cargo_summary "
            + "WHERE routing_status = 'ROUTED' AND last_notified_at IS NULL "
            + "AND booking_status NOT IN ('SETTLED', 'CANCELLED')")
    int countAwaitingNotification();

    /**
     * 確定を待っている予約（S02 / 営業。US13 §受入基準 3）。
     *
     * <p>荷主へ通知したまま確定を忘れると、追跡番号の発行も輸送手配も始まらない。
     * <b>気づく手立てが無いと、期限が近づくまで誰も見つけられない。</b></p>
     *
     * <p><b>件数でなく行を返す。</b> 件数だけでは、営業はどの予約を開けばよいか
     * 分からない（IT4 の「気づく手段は次の行動へ繋ぐ」）。古い通知から順に返す。</p>
     */
    @Select("SELECT booking_id, booking_number, last_notified_at AS notified_at "
            + "FROM cargo_summary WHERE booking_status = 'ROUTE_NOTIFIED' "
            + "ORDER BY last_notified_at LIMIT #{limit}")
    List<AwaitingConfirmationRow> findAwaitingConfirmation(@Param("limit") int limit);

    /** 確定を待っている予約 1 件。 */
    record AwaitingConfirmationRow(
            String bookingId,
            String bookingNumber,
            Instant notifiedAt) {
    }

    /** 予約を確定した（US13）。状態と確定日時だけを書く。 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET booking_status = #{bookingStatus}, "
            + "confirmed_at = #{confirmedAt}, projected_at = #{projectedAt} "
            + "WHERE booking_id = #{bookingId}")
    int updateConfirmed(@Param("bookingId") String bookingId,
            @Param("bookingStatus") String bookingStatus,
            @Param("confirmedAt") Instant confirmedAt,
            @Param("projectedAt") Instant projectedAt);

    /**
     * 見直しを頼まれている予約（S02 / 営業）。
     *
     * <p>件数だけでは仕事が進まないので、予約番号と理由を返して予約詳細へ行けるように
     * する。古い依頼から順に返す（放置されたものを上に出す）。</p>
     */
    List<ConditionReviewRow> findConditionReviews(@Param("limit") int limit);

    /** 見直しを頼まれている予約 1 件。 */
    record ConditionReviewRow(
            String bookingId,
            String bookingNumber,
            String reason,
            Instant requestedAt) {
    }

    /**
     * 経路設計作業一覧（S30）。
     *
     * <p>並び順は<b>誤配が先、そのあと到着期限が近い順</b>（ui_design.md）。誤配は
     * 現在地からの再設計が要り、放っておくほど選べる航海が減る。既定では設計済み
     * （{@code ROUTED}）を外し、誤配は含める。</p>
     */
    List<CargoSummaryRow> findRoutingWorklist(@Param("includeRouted") boolean includeRouted,
            @Param("limit") int limit, @Param("offset") int offset);

    int countRoutingWorklist(@Param("includeRouted") boolean includeRouted);

    /**
     * 一覧の既定条件を検査するためだけの更新。
     *
     * <p>本来 {@code booking_status} は集約のイベントだけが書く。ここで直に更新するのは、
     * 精算まで到達する経路（US23・IT14）がまだ無く、「終了したものを既定で外す」ことを
     * 確かめられないため。<b>本番の経路では使わない。</b></p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET booking_status = 'SETTLED' WHERE booking_id = #{bookingId}")
    int markSettledForTest(@Param("bookingId") String bookingId);

    /**
     * 並び順を検査するためだけの更新。
     *
     * <p>誤配になる経路（US28・IT11）がまだ無く、「誤配が先に出る」ことを確かめられない
     * ため。<b>本番の経路では使わない。</b></p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET routing_status = 'MISROUTED' WHERE booking_id = #{bookingId}")
    int markMisroutedForTest(@Param("bookingId") String bookingId);

    /**
     * 誤配は輸送中に起きる。**その状態の組み合わせでも作業一覧に出ること**を
     * 確かめるための更新。
     *
     * <p>{@code booking_status = 'ROUTE_PROPOSED'} だけで絞っていたころは、
     * 並び順に「誤配が先」と書いてあるのに誤配が 1 件も出なかった。
     * <b>本番の経路では使わない。</b></p>
     */
    @org.apache.ibatis.annotations.Update("UPDATE cargo_summary SET booking_status = 'IN_TRANSIT',"
            + " routing_status = 'MISROUTED' WHERE booking_id = #{bookingId}")
    int markMisroutedInTransitForTest(@Param("bookingId") String bookingId);

    /** 状態ごとの件数（S02 の「今日の作業」）。仮受付は引き渡し待ちを意味する。 */
    @Select("SELECT count(*) FROM cargo_summary WHERE booking_status = #{bookingStatus}")
    int countByStatus(@Param("bookingStatus") String bookingStatus);

    /** 投影の行。shipper_name は鍵破棄後に null になる（ADR-0003）。 */
    record CargoSummaryRow(
            String bookingId,
            String bookingNumber,
            String shipperId,
            String shipperName,
            String trackingNumber,
            String originUnlocode,
            String destinationUnlocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            int quantity,
            String productName,
            String hazardImoClass,
            String hazardUnNumber,
            BigDecimal temperatureMinC,
            BigDecimal temperatureMaxC,
            String bookingStatus,
            String routingStatus,
            Instant bookedAt,
            Instant routingRequestedAt,
            Instant updatedAt,
            String updatedBy,
            Instant lastNotifiedAt,
            Instant returnedToRoutingAt,
            String returnReason,
            // 調整済みの探索条件（US10）。**予約の読み口からも読める**ようにする。
            // 候補算出の応答にだけ載せると、探索が落ちている間だけ画面から条件が
            // 消え、経路設計者は直せる手段を失う（IT6 引き継ぎ 8b）。
            String routeExcludeUnlocodes,
            String routeDepartFromUnlocode,
            // 確定した日時（US13）。未確定なら null。
            Instant confirmedAt,
            Instant projectedAt,
            String lastEventId) {
    }
}
