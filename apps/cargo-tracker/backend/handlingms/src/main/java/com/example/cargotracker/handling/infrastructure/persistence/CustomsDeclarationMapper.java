package com.example.cargotracker.handling.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 通関申告の現在状態（US29）。正典は data-model.md「customs_declaration」。 */
@Mapper
public interface CustomsDeclarationMapper {

    /** 読み出す列を並べる。<b>{@code SELECT *} にしない</b>（列順で割り当てられる）。 */
    String COLUMNS = "declaration_number, tracking_number, booking_id, status, declared_at, "
            + "last_status_changed_at, last_held_at, last_reason, "
            + "changed_by, projected_at";

    /** 申告を 1 行足す。主キーは申告番号なので、読み直しても増えない。 */
    int insert(CustomsDeclarationRow row);

    /** 状態を書き換える。**履歴はここではなく Event Store が持つ。** */
    int updateStatus(CustomsStatusChange change);

    /**
     * 状態の更新 1 回ぶん。
     *
     * <p><b>並べた引数にしない。</b> 7 つ並ぶと、呼び側で順序を 1 つ違えても
     * 型が合ってしまう（{@code changedAt} と {@code projectedAt} はどちらも
     * {@code Instant}）。名前で渡す。</p>
     *
     * @param lastHeldAt 留置に入った時刻。留置以外では {@code null} を渡し、
     *     既存の値を残す（いつから留置だったかを消さない）
     */
    record CustomsStatusChange(
            String declarationNumber,
            String status,
            String reason,
            String changedBy,
            Instant lastHeldAt,
            Instant changedAt,
            Instant projectedAt) {
    }

    @Select("SELECT " + COLUMNS + " FROM customs_declaration "
            + "WHERE declaration_number = #{declarationNumber}")
    CustomsDeclarationRow findByNumber(
            @Param("declarationNumber") String declarationNumber);

    /**
     * その貨物の未決着の申告（不変条件 3）。
     *
     * <p><b>集約では守れない。</b> 1 申告 1 集約なので、集約は他の申告を知らない
     * （申告番号が違えば別の集約になる）。同じ貨物に未決着の申告が 2 件あると、
     * 引取のガードがどちらを見るかで結果が変わる。</p>
     */
    @Select("SELECT " + COLUMNS + " FROM customs_declaration "
            + "WHERE tracking_number = #{trackingNumber} "
            + "  AND status IN ('PENDING', 'HELD') "
            + "ORDER BY declared_at DESC")
    List<CustomsDeclarationRow> findUnsettledByCargo(
            @Param("trackingNumber") String trackingNumber);

    /**
     * その貨物の最新の申告（引取のガードが読む）。
     *
     * <p>決着したものも含める。<b>通関済であることを見るため</b>である。</p>
     */
    @Select("SELECT " + COLUMNS + " FROM customs_declaration "
            + "WHERE tracking_number = #{trackingNumber} "
            + "ORDER BY last_status_changed_at DESC, declared_at DESC LIMIT 1")
    CustomsDeclarationRow findLatestByCargo(@Param("trackingNumber") String trackingNumber);

    /**
     * 一覧（S52）。絞りは XML 側で組む。
     *
     * <p><b>留置日数で絞らない・並べない。</b> 留置中は日が経つだけで日数が変わるが、
     * イベントは来ないので列は古いままになる。<b>数えるのは読むとき</b>で、
     * 休日カレンダーを知っているのはドメインである——SQL に写すと、同じ判定が
     * 2 か所になって片方だけ直る。</p>
     */
    List<CustomsDeclarationRow> search(@Param("includeCleared") boolean includeCleared,
            @Param("trackingNumber") String trackingNumber,
            @Param("status") String status,
            @Param("limit") int limit);

    /**
     * その港の貨物ごとの最新の申告（引取待ち一覧が読む）。
     *
     * <p><b>1 件ずつ引かない。</b> 引取待ちは港ごとに数十件あり、行ごとに問い合わせると
     * N+1 になる。<b>貨物ごとに 1 行だけ</b>返す——同じ貨物に複数の申告があるとき
     * （不可のあとに出し直した場合）は、引取のガードと同じ順（最後に状態が変わった
     * ものが先）で最初の 1 件を採る。</p>
     */
    List<CustomsDeclarationRow> findLatestByCargos(
            @Param("trackingNumbers") List<String> trackingNumbers);

    /** 留置中の申告（督促の判定はこれを読んで数える）。 */
    @Select("SELECT " + COLUMNS + " FROM customs_declaration WHERE status = 'HELD'")
    List<CustomsDeclarationRow> findHeld();

    record CustomsDeclarationRow(
            String declarationNumber,
            String trackingNumber,
            String bookingId,
            String status,
            Instant declaredAt,
            Instant lastStatusChangedAt,
            Instant lastHeldAt,
            String lastReason,
            String changedBy,
            Instant projectedAt) {
    }
}
